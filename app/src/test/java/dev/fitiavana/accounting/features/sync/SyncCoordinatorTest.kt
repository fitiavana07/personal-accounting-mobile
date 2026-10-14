package dev.fitiavana.accounting.features.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.features.accounts.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Two devices, each with a database, settings and a coordinator, finding each other through a
 * fake network and syncing over real loopback connections.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncCoordinatorTest {
    private lateinit var a: SyncDevice
    private lateinit var b: SyncDevice
    private val identity = SyncIdentity.RELEASE
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Delivers broadcasts and unicasts to every other open channel, as if on the same Wi-Fi. */
    private class FakeNetwork(private val identity: SyncIdentity) {
        // Appended to by the coordinators' threads while the test reads them.
        val channels = CopyOnWriteArrayList<FakeChannel>()
        val broadcasts = CopyOnWriteArrayList<DiscoveryPacket>()

        fun channel() = FakeChannel(this).also(channels::add)

        class FakeChannel(private val network: FakeNetwork) : DiscoveryChannel {
            private val received = MutableSharedFlow<Datagram>(extraBufferCapacity = 64)

            @Volatile
            var closed = false

            /** While true, nothing reaches this channel: the device does not hear anyone. */
            @Volatile
            var deaf = false

            override val incoming: Flow<Datagram> = received

            override suspend fun broadcast(bytes: ByteArray) {
                DiscoveryPacket.decode(bytes, network.identity)?.let { network.broadcasts += it }
                deliver(bytes)
            }

            override suspend fun send(bytes: ByteArray, host: String) = deliver(bytes)

            override fun close() {
                closed = true
            }

            fun hear(bytes: ByteArray) {
                if (!closed && !deaf) received.tryEmit(Datagram(bytes, "127.0.0.1"))
            }

            private fun deliver(bytes: ByteArray) {
                if (closed) return
                network.channels.filter { it !== this }.forEach { it.hear(bytes) }
            }
        }
    }

    private class Node(val device: SyncDevice, val settings: SyncSettingsStore, val coordinator: SyncCoordinator)

    private val network = FakeNetwork(identity)
    private val nodes = mutableListOf<Node>()
    private val devices = mutableListOf<SyncDevice>()
    private var counter = 0

    @Before
    fun setUp() {
        a = newDevice("dev-a")
        b = newDevice("dev-b")
    }

    private fun newDevice(id: String) = SyncDevice(id, 1_000).also(devices::add)

    private fun settings(deviceId: String, passphrase: String?, autoSync: Boolean = true) =
        SyncSettingsStore(
            ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("sync-test-${counter++}", Context.MODE_PRIVATE),
            newId = { deviceId },
        ).apply {
            setPassphrase(passphrase)
            setAutoSync(autoSync)
        }

    private fun coordinator(device: SyncDevice, settings: SyncSettingsStore, newChannel: () -> DiscoveryChannel = network::channel) =
        SyncCoordinator(
            scope = scope,
            repository = device.sync,
            settings = settings,
            changes = device.tracker.changes,
            identity = identity,
            newChannel = newChannel,
            debounceMs = 100,
            announceIntervalMs = 200,
        )

    private fun node(device: SyncDevice, passphrase: String? = "correct horse", autoSync: Boolean = true): Node {
        val settings = settings(device.id, passphrase, autoSync)
        return Node(device, settings, coordinator(device, settings)).also(nodes::add)
    }

    @After
    fun tearDown() {
        nodes.forEach { it.coordinator.stop() }
        scope.cancel()
        devices.forEach { it.close() }
    }

    private suspend fun waitUntil(what: String, condition: suspend () -> Boolean) {
        try {
            withTimeout(15_000) { while (!condition()) delay(25) }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("Timed out waiting for: $what\n" + nodes.joinToString("\n") { it.coordinator.state.value.toString() })
        }
    }

    private fun accountIds(d: SyncDevice) = d.db.accountDao().getAllSync().map { it.id }.sorted()

    private fun nameOf(d: SyncDevice, id: String) = d.db.accountDao().getById(id)?.name

    @Test
    fun `without a passphrase nothing is started`() = runBlocking {
        val n = node(a, passphrase = null)

        n.coordinator.start()
        waitUntil("running") { n.coordinator.state.value.running }
        delay(300)

        assertFalse(n.coordinator.state.value.hasPassphrase)
        assertTrue(network.channels.isEmpty())
        assertTrue(n.coordinator.state.value.peers.isEmpty())
    }

    @Test
    fun `devices with the same passphrase find each other and sync on their own`() = runBlocking {
        a.seedLedger()
        val first = node(a)
        val second = node(b)

        first.coordinator.start()
        second.coordinator.start()

        waitUntil("B receives A's data") { accountIds(b) == listOf("a1", "a2") }
        waitUntil("both list a synced peer") {
            first.coordinator.state.value.peers.singleOrNull()?.sync is PeerSync.Synced &&
                second.coordinator.state.value.peers.singleOrNull()?.sync is PeerSync.Synced
        }
        assertEquals(DeviceName.of("dev-b"), first.coordinator.state.value.peers.single().peer.label)
        assertEquals(DeviceName.of("dev-a"), second.coordinator.state.value.peers.single().peer.label)
        assertEquals(100L, b.db.accountBalanceDao().getByAccountId("a1")?.balance)
        assertEquals(a.state(), b.state())
    }

    @Test
    fun `whichever device holds the data both end up with it`() = runBlocking {
        b.seedLedger()

        node(a).coordinator.start()
        node(b).coordinator.start()

        waitUntil("A receives B's data") { accountIds(a) == listOf("a1", "a2") }
    }

    @Test
    fun `a local change is pushed to the peers and they can answer in kind`() = runBlocking {
        a.seedLedger()
        node(a).coordinator.start()
        node(b).coordinator.start()
        waitUntil("first sync") { accountIds(b) == listOf("a1", "a2") }

        b.now = 5_000
        b.accounts.update(Account("a1", "From B", "asset"))
        waitUntil("A sees B's edit") { nameOf(a, "a1") == "From B" }

        a.now = 9_000
        a.accounts.update(Account("a1", "From A", "asset"))
        waitUntil("B sees A's edit") { nameOf(b, "a1") == "From A" }
    }

    @Test
    fun `a change made before the peer was seen is pushed as soon as it appears`() = runBlocking {
        val first = node(a)
        first.coordinator.start()
        waitUntil("A listening") { network.channels.size == 1 }
        val second = node(b) // the larger id: it does not start sessions just because a peer appears
        second.coordinator.start()
        waitUntil("B listening") { network.channels.size == 2 }
        val bChannel = network.channels[1]
        bChannel.deaf = true
        waitUntil("A knows B") { first.coordinator.state.value.peers.any { it.sync is PeerSync.Synced } }

        b.accounts.insert(Account("piano", "Piano", "asset"))
        delay(500) // the debounced push finds nobody to push to
        assertEquals(emptyList<String>(), accountIds(a))
        bChannel.deaf = false

        waitUntil("A receives what B did while deaf") { accountIds(a) == listOf("piano") }
    }

    @Test
    fun `with auto-sync off nothing moves until the user asks`() = runBlocking {
        a.seedLedger()
        val first = node(a, autoSync = false)
        val second = node(b, autoSync = false)
        first.coordinator.start()
        second.coordinator.start()
        waitUntil("peers visible") {
            first.coordinator.state.value.peers.isNotEmpty() && second.coordinator.state.value.peers.isNotEmpty()
        }
        delay(500)
        assertEquals(emptyList<String>(), accountIds(b))

        first.coordinator.syncWith("dev-b")

        waitUntil("manual sync") { accountIds(b) == listOf("a1", "a2") }
    }

    @Test
    fun `the sync all button syncs every visible peer`() = runBlocking {
        a.seedLedger()
        val first = node(a, autoSync = false)
        val second = node(b, autoSync = false)
        first.coordinator.start()
        second.coordinator.start()
        waitUntil("peer visible") { second.coordinator.state.value.peers.isNotEmpty() }

        second.coordinator.syncAll()

        waitUntil("B pulls A's data") { accountIds(b) == listOf("a1", "a2") }
    }

    @Test
    fun `devices with different passphrases never see each other`() = runBlocking {
        a.seedLedger()
        val first = node(a, passphrase = "one passphrase")
        val second = node(b, passphrase = "two passphrase")

        first.coordinator.start()
        second.coordinator.start()
        delay(1_000)

        assertTrue(first.coordinator.state.value.peers.isEmpty())
        assertTrue(second.coordinator.state.value.peers.isEmpty())
        assertEquals(emptyList<String>(), accountIds(b))
    }

    @Test
    fun `entering the same passphrase joins the group`() = runBlocking {
        a.seedLedger()
        node(a, passphrase = "shared passphrase").coordinator.start()
        val second = node(b, passphrase = "other passphrase")
        second.coordinator.start()
        delay(500)
        assertEquals(emptyList<String>(), accountIds(b))

        second.settings.setPassphrase("shared passphrase")

        waitUntil("B joins and syncs") { accountIds(b) == listOf("a1", "a2") }
    }

    @Test
    fun `stopping ends discovery, syncing and the list of peers`() = runBlocking {
        a.seedLedger()
        val first = node(a)
        val second = node(b)
        first.coordinator.start()
        second.coordinator.start()
        waitUntil("first sync") { accountIds(b) == listOf("a1", "a2") }

        first.coordinator.stop()
        second.coordinator.stop()
        b.now = 5_000
        b.accounts.update(Account("a1", "After stop", "asset"))
        delay(800)

        assertEquals("Bank a1", nameOf(a, "a1"))
        assertFalse(first.coordinator.state.value.running)
        assertTrue(first.coordinator.state.value.peers.isEmpty())
        assertTrue(network.channels.all { it.closed })
    }

    @Test
    fun `a peer that cannot be reached is shown as failed`() = runBlocking {
        val first = node(a)
        first.coordinator.start()
        waitUntil("listening") { network.channels.isNotEmpty() }
        val group = SyncCrypto("correct horse", identity).groupTag

        // A phantom peer whose announced port nobody listens on; "zzz" sorts after "dev-a", so dev-a initiates.
        network.channels.single().hear(
            DiscoveryPacket(DiscoveryPacket.Type.ANNOUNCE, PeerInfo("zzz", "Ghost"), port = 1, group = group).encode(identity),
        )

        waitUntil("failure shown") {
            (first.coordinator.state.value.peers.singleOrNull()?.sync as? PeerSync.Failed)?.failure is SessionFailure.Network
        }
    }

    @Test
    fun `a network that cannot be opened is reported and nothing crashes`() = runBlocking {
        val failing = coordinator(a, settings("dev-a", "correct horse"), newChannel = { throw java.net.BindException("Address already in use") })

        failing.start()

        waitUntil("the problem is reported") { failing.state.value.networkError != null }
        assertTrue(failing.state.value.networkError!!.contains("Address already in use"))
        assertTrue(failing.state.value.running)
        failing.stop()
    }

    @Test
    fun `refreshing tries to open the network again`() = runBlocking {
        var failing = true
        val coordinator = coordinator(a, settings("dev-a", "correct horse"), newChannel = {
            if (failing) throw java.net.BindException("busy") else network.channel()
        })
        coordinator.start()
        waitUntil("the problem is reported") { coordinator.state.value.networkError != null }

        failing = false
        coordinator.refresh()

        waitUntil("the problem is cleared") { coordinator.state.value.networkError == null && network.channels.isNotEmpty() }
        coordinator.stop()
    }

    @Test
    fun `refresh asks the network who is there`() = runBlocking {
        val first = node(a)
        first.coordinator.start()
        waitUntil("listening") { network.broadcasts.isNotEmpty() }

        first.coordinator.refresh()

        waitUntil("query sent") { network.broadcasts.any { it.type == DiscoveryPacket.Type.QUERY } }
    }

    @Test
    fun `the state tells this device's name and when it last synced`() = runBlocking {
        a.seedLedger()
        val first = node(a)
        node(b).coordinator.start()
        first.coordinator.start()

        waitUntil("synced") { accountIds(b) == listOf("a1", "a2") && first.coordinator.state.value.lastSyncedAt != null }

        assertEquals(DeviceName.of("dev-a"), first.coordinator.state.value.deviceName)
        assertTrue(first.coordinator.state.value.autoSync)
        assertTrue(first.coordinator.state.value.hasPassphrase)
    }
}
