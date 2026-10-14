package dev.fitiavana.accounting.features.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerRegistryTest {
    private var now = 0L
    private val registry = PeerRegistry(selfId = "self", group = "grp", ttlMillis = 15_000, now = { now })

    private fun packet(
        id: String,
        name: String = "Name of $id",
        port: Int = 4000,
        group: String = "grp",
        type: DiscoveryPacket.Type = DiscoveryPacket.Type.ANNOUNCE,
    ) = DiscoveryPacket(type, PeerInfo(id, name), port, group)

    private val ids get() = registry.peers.value.map { it.info.deviceId }

    @Test
    fun aPeerSeenIsListedWithItsAddressAndPort() {
        assertTrue(registry.seen(packet("a", "Brave Otter", port = 4242), "192.168.1.20"))

        val peer = registry.peers.value.single()
        assertEquals(PeerInfo("a", "Brave Otter"), peer.info)
        assertEquals("192.168.1.20", peer.host)
        assertEquals(4242, peer.port)
    }

    @Test
    fun thisDeviceIsNeverListed() {
        assertFalse(registry.seen(packet("self"), "192.168.1.5"))

        assertTrue(registry.peers.value.isEmpty())
    }

    @Test
    fun devicesOfAnotherGroupAreNeverListed() {
        assertFalse(registry.seen(packet("a", group = "someone-elses"), "192.168.1.20"))

        assertTrue(registry.peers.value.isEmpty())
    }

    @Test
    fun seeingTheSamePeerAgainChangesNothingUnlessItsAddressDid() {
        registry.seen(packet("a"), "192.168.1.20")

        assertFalse(registry.seen(packet("a"), "192.168.1.20"))
        assertTrue(registry.seen(packet("a", port = 5000), "192.168.1.20"))
        assertTrue(registry.seen(packet("a", port = 5000), "192.168.1.99"))
        assertEquals("192.168.1.99", registry.peers.value.single().host)
        assertEquals(5000, registry.peers.value.single().port)
    }

    @Test
    fun aPeerExpiresAfterTheTtlWithoutNews() {
        registry.seen(packet("a"), "h")

        now = 14_999
        assertFalse(registry.expire())
        assertEquals(listOf("a"), ids)

        now = 15_001
        assertTrue(registry.expire())
        assertTrue(registry.peers.value.isEmpty())
    }

    @Test
    fun aPeerHeardAgainStaysPastTheOriginalTtl() {
        registry.seen(packet("a"), "h")
        now = 10_000
        registry.seen(packet("a"), "h")

        now = 20_000
        registry.expire()

        assertEquals(listOf("a"), ids)
    }

    @Test
    fun expiringOnlyDropsTheStalePeers() {
        registry.seen(packet("old"), "h1")
        now = 10_000
        registry.seen(packet("new"), "h2")

        now = 16_000
        registry.expire()

        assertEquals(listOf("new"), ids)
    }

    @Test
    fun clearForgetsEveryone() {
        registry.seen(packet("a"), "h")
        registry.seen(packet("b"), "h")

        registry.clear()

        assertTrue(registry.peers.value.isEmpty())
    }

    @Test
    fun peersAreListedByLabel() {
        registry.seen(packet("1", "Zesty Zebra"), "h")
        registry.seen(packet("2", "Agile Ant"), "h")
        registry.seen(packet("3", "Mighty Moose"), "h")

        assertEquals(listOf("Agile Ant", "Mighty Moose", "Zesty Zebra"), registry.peers.value.map { it.label })
    }

    @Test
    fun twoPeersWithTheSameNameAreToldApartByTheirShortId() {
        registry.seen(packet("1a2b-xxxx", "Brave Otter"), "h")
        registry.seen(packet("3c4d-yyyy", "Brave Otter"), "h")
        registry.seen(packet("5e6f-zzzz", "Agile Ant"), "h")

        assertEquals(
            listOf("Agile Ant", "Brave Otter · 1A2B", "Brave Otter · 3C4D"),
            registry.peers.value.map { it.label },
        )
    }

    @Test
    fun theSuffixGoesAwayWhenTheNamesakeLeaves() {
        registry.seen(packet("1a2b-xxxx", "Brave Otter"), "h")
        now = 10_000
        registry.seen(packet("3c4d-yyyy", "Brave Otter"), "h")

        now = 16_000
        registry.expire()

        assertEquals(listOf("Brave Otter"), registry.peers.value.map { it.label })
    }
}
