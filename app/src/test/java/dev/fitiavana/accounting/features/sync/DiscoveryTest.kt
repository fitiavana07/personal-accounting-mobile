package dev.fitiavana.accounting.features.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class DiscoveryTest {
    private class FakeChannel : DiscoveryChannel {
        val received = MutableSharedFlow<Datagram>(extraBufferCapacity = 64)
        val broadcasts = mutableListOf<ByteArray>()
        val sent = mutableListOf<Pair<ByteArray, String>>()
        var closed = false

        override val incoming: Flow<Datagram> = received

        override suspend fun broadcast(bytes: ByteArray) {
            broadcasts += bytes
        }

        override suspend fun send(bytes: ByteArray, host: String) {
            sent += bytes to host
        }

        override fun close() {
            closed = true
        }
    }

    private val identity = SyncIdentity.RELEASE
    private val self = PeerInfo("self", "Brave Otter")
    private val other = PeerInfo("other", "Agile Ant")
    private val channel = FakeChannel()

    private fun TestScope.registry() = PeerRegistry("self", "grp", 15_000) { currentTime }

    private fun TestScope.discovery(registry: PeerRegistry) =
        Discovery(backgroundScope, channel, registry, self, port = 4000, group = "grp", identity = identity, announceIntervalMs = 5_000)

    private fun packet(
        info: PeerInfo = other,
        type: DiscoveryPacket.Type = DiscoveryPacket.Type.ANNOUNCE,
        group: String = "grp",
        from: SyncIdentity = identity,
    ) = DiscoveryPacket(type, info, 5000, group).encode(from)

    private suspend fun TestScope.hear(bytes: ByteArray, host: String = "192.168.1.20") {
        channel.received.emit(Datagram(bytes, host))
        runCurrent()
    }

    private fun decode(bytes: ByteArray) = DiscoveryPacket.decode(bytes, identity)!!

    @Test
    fun `announces at once then every interval`() = runTest {
        val discovery = discovery(registry())

        discovery.start()
        runCurrent()
        assertEquals(1, channel.broadcasts.size)

        advanceTimeBy(5_001)
        assertEquals(2, channel.broadcasts.size)
        advanceTimeBy(5_000)
        assertEquals(3, channel.broadcasts.size)

        val announced = decode(channel.broadcasts.first())
        assertEquals(DiscoveryPacket.Type.ANNOUNCE, announced.type)
        assertEquals(self, announced.info)
        assertEquals(4000, announced.port)
        assertEquals("grp", announced.group)
        discovery.stop()
    }

    @Test
    fun `an announcing peer appears in the list`() = runTest {
        val registry = registry()
        discovery(registry).start()
        runCurrent()

        hear(packet(), host = "192.168.1.20")

        val peer = registry.peers.value.single()
        assertEquals(other, peer.info)
        assertEquals("192.168.1.20", peer.host)
        assertEquals(5000, peer.port)
    }

    @Test
    fun `own, foreign and garbage packets are ignored`() = runTest {
        val registry = registry()
        discovery(registry).start()
        runCurrent()

        hear(packet(info = self))
        hear(packet(group = "someone-elses"))
        hear("garbage".toByteArray(), "192.168.1.30")

        assertTrue(registry.peers.value.isEmpty())
    }

    @Test
    fun `packets of the other build flavor are ignored`() = runTest {
        val registry = registry()
        discovery(registry).start()
        runCurrent()

        hear(packet(from = SyncIdentity.DEBUG))
        hear(packet(type = DiscoveryPacket.Type.QUERY, from = SyncIdentity.DEBUG))

        assertTrue(registry.peers.value.isEmpty())
        assertTrue(channel.sent.isEmpty())
    }

    @Test
    fun `a query is answered directly with an announcement and the asker is listed`() = runTest {
        val registry = registry()
        discovery(registry).start()
        runCurrent()

        hear(packet(type = DiscoveryPacket.Type.QUERY), host = "192.168.1.20")

        val (bytes, host) = channel.sent.single()
        assertEquals("192.168.1.20", host)
        assertEquals(DiscoveryPacket.Type.ANNOUNCE, decode(bytes).type)
        assertEquals(self, decode(bytes).info)
        assertEquals(listOf(other), registry.peers.value.map { it.info })
    }

    @Test
    fun `a query from another group or from us gets no answer`() = runTest {
        discovery(registry()).start()
        runCurrent()

        hear(packet(type = DiscoveryPacket.Type.QUERY, group = "someone-elses"))
        hear(packet(info = self, type = DiscoveryPacket.Type.QUERY))

        assertTrue(channel.sent.isEmpty())
    }

    @Test
    fun `a peer that goes quiet disappears and one that keeps announcing stays`() = runTest {
        val registry = registry()
        discovery(registry).start()
        runCurrent()
        hear(packet(info = other))
        hear(packet(info = PeerInfo("quiet", "Quiet Quail")))

        repeat(4) {
            advanceTimeBy(4_000)
            hear(packet(info = other))
        }

        assertEquals(listOf("other"), registry.peers.value.map { it.info.deviceId })
    }

    @Test
    fun `refresh forgets everyone and asks who is there`() = runTest {
        val registry = registry()
        val discovery = discovery(registry)
        discovery.start()
        runCurrent()
        hear(packet())

        discovery.refresh()
        runCurrent()

        assertTrue(registry.peers.value.isEmpty())
        val asked = decode(channel.broadcasts.last())
        assertEquals(DiscoveryPacket.Type.QUERY, asked.type)
        assertEquals(self, asked.info)
        assertEquals(4000, asked.port)
    }

    @Test
    fun `peers answer a refresh and are listed again`() = runTest {
        val registry = registry()
        val discovery = discovery(registry)
        discovery.start()
        runCurrent()
        hear(packet())
        discovery.refresh()
        runCurrent()

        hear(packet())

        assertEquals(listOf(other), registry.peers.value.map { it.info })
    }

    @Test
    fun `stopping ends the announcements and closes the channel`() = runTest {
        val discovery = discovery(registry())
        discovery.start()
        runCurrent()
        val before = channel.broadcasts.size

        discovery.stop()
        advanceTimeBy(30_000)

        assertEquals(before, channel.broadcasts.size)
        assertTrue(channel.closed)
    }

    @Test
    fun `stopping forgets the peers`() = runTest {
        val registry = registry()
        val discovery = discovery(registry)
        discovery.start()
        runCurrent()
        hear(packet())

        discovery.stop()

        assertFalse(registry.peers.value.isNotEmpty())
    }
}
