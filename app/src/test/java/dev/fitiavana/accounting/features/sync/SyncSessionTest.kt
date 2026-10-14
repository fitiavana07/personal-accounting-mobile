package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.features.accounts.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** Whole sessions between two devices over a real loopback connection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncSessionTest {
    private lateinit var a: SyncDevice
    private lateinit var b: SyncDevice
    private val peerA = PeerInfo("dev-a", "Strategic Banana")
    private val peerB = PeerInfo("dev-b", "Brave Otter")
    private val crypto = SyncCrypto("correct horse", SyncIdentity.RELEASE)
    private val loopback = InetAddress.getLoopbackAddress()

    @Before
    fun setUp() {
        a = SyncDevice("dev-a", 1_000)
        b = SyncDevice("dev-b", 2_000)
    }

    @After
    fun tearDown() {
        a.close()
        b.close()
    }

    private fun session(d: SyncDevice, peer: PeerInfo, crypto: SyncCrypto = this.crypto, repository: SyncRepository = d.sync) =
        SyncSession(repository, crypto, peer)

    /** Runs the responder on a server socket and the initiator against it; returns (initiator, responder). */
    private suspend fun run(initiator: SyncSession, responder: SyncSession): Pair<SessionResult, SessionResult> =
        coroutineScope {
            ServerSocket(0, 1, loopback).use { server ->
                val answering = async(Dispatchers.IO) {
                    server.accept().use { socket ->
                        socket.soTimeout = TIMEOUT
                        responder.respond(socket.getInputStream(), socket.getOutputStream())
                    }
                }
                val asking = withContext(Dispatchers.IO) {
                    Socket(loopback, server.localPort).use { socket ->
                        socket.soTimeout = TIMEOUT
                        initiator.initiate(socket.getInputStream(), socket.getOutputStream())
                    }
                }
                asking to answering.await()
            }
        }

    private fun accountIds(d: SyncDevice) = d.db.accountDao().getAllSync().map { it.id }.sorted()

    @Test
    fun `a session brings both devices to the same state`() = runBlocking {
        a.seedLedger()

        val (initiator, responder) = run(session(a, peerA), session(b, peerB))

        assertEquals(SessionResult.Synced(peerB, changed = false), initiator)
        assertEquals(SessionResult.Synced(peerA, changed = true), responder)
        assertEquals(listOf("a1", "a2"), accountIds(b))
        assertEquals(100L, b.db.accountBalanceDao().getByAccountId("a1")?.balance)
        assertEquals(a.state(), b.state())
    }

    @Test
    fun `a change made on one device is on the other after the next session`() = runBlocking {
        a.seedLedger()
        run(session(a, peerA), session(b, peerB))

        b.now = 5_000
        b.accounts.update(Account("a1", "Renamed", "asset"))
        val (initiator, _) = run(session(b, peerB), session(a, peerA))

        assertEquals(SessionResult.Synced(peerA, changed = false), initiator)
        assertEquals("Renamed", a.db.accountDao().getById("a1")?.name)
    }

    @Test
    fun `the initiator learns what the responder already had`() = runBlocking {
        a.seedLedger()
        b.now = 5_000
        b.accounts.insert(Account("b1", "Piano", "asset"))

        val (initiator, _) = run(session(a, peerA), session(b, peerB))

        assertEquals(SessionResult.Synced(peerB, changed = true), initiator)
        assertEquals(listOf("a1", "a2", "b1"), accountIds(a))
        assertEquals(listOf("a1", "a2", "b1"), accountIds(b))
    }

    @Test
    fun `a wrong passphrase syncs nothing and tells both sides`() = runBlocking {
        a.seedLedger()

        val (initiator, responder) = run(session(a, peerA), session(b, peerB, SyncCrypto("other passphrase", SyncIdentity.RELEASE)))

        assertEquals(SessionResult.Failed(SessionFailure.NoAnswer), initiator)
        assertEquals(SessionResult.Failed(SessionFailure.WrongPassphrase), responder)
        assertEquals(emptyList<String>(), accountIds(b))
    }

    @Test
    fun `a release device and a debug device with the same passphrase cannot sync`() = runBlocking {
        a.seedLedger()
        val debugCrypto = SyncCrypto("correct horse", SyncIdentity.DEBUG)

        val (initiator, responder) = run(session(a, peerA), session(b, peerB, debugCrypto))

        assertEquals(SessionResult.Failed(SessionFailure.NoAnswer), initiator)
        assertEquals(SessionResult.Failed(SessionFailure.WrongPassphrase), responder)
        assertEquals(emptyList<String>(), accountIds(b))
    }

    @Test
    fun `a peer on another database version refuses with a reason`() = runBlocking {
        a.seedLedger()
        val other = session(b, peerB, repository = b.syncRepository(AppDatabase.SCHEMA_VERSION + 1))

        val (initiator, responder) = run(session(a, peerA), other)

        val refused = (initiator as SessionResult.Failed).failure as SessionFailure.Refused
        assertTrue(refused.message.contains("version"))
        assertTrue((responder as SessionResult.Failed).failure is SessionFailure.Refused)
        assertEquals(emptyList<String>(), accountIds(b))
    }

    @Test
    fun `a peer on another protocol version is told so and nothing changes`() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            val answering = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = TIMEOUT
                    session(b, peerB).respond(socket.getInputStream(), socket.getOutputStream())
                }
            }
            val reply = withContext(Dispatchers.IO) {
                Socket(loopback, server.localPort).use { socket ->
                    socket.soTimeout = TIMEOUT
                    val request = JSONObject(SyncMessageJson.encode(SyncMessage.Request(peerA, a.sync.snapshot())))
                        .put("protocol", 99).toString()
                    FrameIO.write(socket.getOutputStream(), crypto.encrypt(request.toByteArray()))
                    String(crypto.decrypt(FrameIO.read(socket.getInputStream())))
                }
            }

            assertTrue(SyncMessageJson.decode(reply) is SyncMessageJson.DecodeResult.Success)
            assertTrue(((answering.await() as SessionResult.Failed).failure) is SessionFailure.Incompatible)
        }
    }

    @Test
    fun `garbage from a stranger is rejected without a reply or a change`() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            val answering = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = TIMEOUT
                    session(b, peerB).respond(socket.getInputStream(), socket.getOutputStream())
                }
            }
            withContext(Dispatchers.IO) {
                Socket(loopback, server.localPort).use { socket ->
                    FrameIO.write(socket.getOutputStream(), ByteArray(64) { it.toByte() })
                }
            }

            assertEquals(SessionResult.Failed(SessionFailure.WrongPassphrase), answering.await())
            assertEquals(emptyList<String>(), accountIds(b))
        }
    }

    @Test
    fun `an oversized frame is refused as a network problem`() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            val answering = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = TIMEOUT
                    session(b, peerB).respond(socket.getInputStream(), socket.getOutputStream())
                }
            }
            withContext(Dispatchers.IO) {
                Socket(loopback, server.localPort).use { socket ->
                    socket.getOutputStream().write(byteArrayOf(0x7f, -1, -1, -1))
                    socket.getOutputStream().flush()
                }
            }

            assertTrue((answering.await() as SessionResult.Failed).failure is SessionFailure.Network)
        }
    }

    @Test
    fun `a peer that goes silent times out instead of hanging`() = runBlocking {
        ServerSocket(0, 1, loopback).use { server ->
            val silent = async(Dispatchers.IO) { server.accept().use { Thread.sleep(1_500) } }
            val result = withContext(Dispatchers.IO) {
                Socket(loopback, server.localPort).use { socket ->
                    socket.soTimeout = 300
                    session(a, peerA).initiate(socket.getInputStream(), socket.getOutputStream())
                }
            }

            assertTrue((result as SessionResult.Failed).failure is SessionFailure.Network)
            silent.await()
        }
    }

    private companion object {
        const val TIMEOUT = 5_000
    }
}
