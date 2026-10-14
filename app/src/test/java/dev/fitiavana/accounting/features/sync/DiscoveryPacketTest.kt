package dev.fitiavana.accounting.features.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class DiscoveryPacketTest {
    private val identity = SyncIdentity.RELEASE
    private val packet = DiscoveryPacket(
        type = DiscoveryPacket.Type.ANNOUNCE,
        info = PeerInfo("3f2b9c1e-1111", "Strategic Banana"),
        port = 40123,
        group = "0123456789abcdef",
    )

    private fun encoded() = JSONObject(String(packet.encode(identity)))

    private fun decode(bytes: ByteArray) = DiscoveryPacket.decode(bytes, identity)

    @Test
    fun aPacketRoundTrips() {
        assertEquals(packet, decode(packet.encode(identity)))
        val query = packet.copy(type = DiscoveryPacket.Type.QUERY)
        assertEquals(query, decode(query.encode(identity)))
    }

    @Test
    fun aPacketIsSmallEnoughForOneDatagram() {
        assertTrue(packet.encode(identity).size < 512)
    }

    @Test
    fun packetsThatAreNotOursAreIgnored() {
        assertNull(decode("hello".toByteArray()))
        assertNull(decode(ByteArray(0)))
        assertNull(decode("""{"app": "someone-else", "protocol": 1}""".toByteArray()))
        assertNull(decode(ByteArray(64) { it.toByte() }))
    }

    @Test
    fun aPacketOfAnotherProtocolVersionIsIgnored() {
        val other = encoded().put("protocol", PROTOCOL_VERSION + 1)

        assertNull(decode(other.toString().toByteArray()))
    }

    @Test
    fun aPacketWithMissingOrWrongFieldsIsIgnored() {
        fun without(key: String) = encoded().apply { remove(key) }.toString().toByteArray()

        listOf("type", "deviceId", "name", "port", "group").forEach { assertNull("without $it", decode(without(it))) }
        val wrongPort = encoded().put("port", "40123").toString().toByteArray()
        val badPort = encoded().put("port", 70_000).toString().toByteArray()
        val badType = encoded().put("type", "shout").toString().toByteArray()
        assertNull(decode(wrongPort))
        assertNull(decode(badPort))
        assertNull(decode(badType))
    }

    @Test
    fun aPacketLargerThanOneKilobyteIsIgnored() {
        val big = packet.copy(info = PeerInfo("id", "x".repeat(1100)))

        assertNull(decode(big.encode(identity)))
    }

    @Test
    fun aDebugPacketIsIgnoredByReleaseAndTheOtherWayAround() {
        val fromDebug = packet.encode(SyncIdentity.DEBUG)
        val fromRelease = packet.encode(SyncIdentity.RELEASE)

        assertNull(DiscoveryPacket.decode(fromDebug, SyncIdentity.RELEASE))
        assertNull(DiscoveryPacket.decode(fromRelease, SyncIdentity.DEBUG))
        assertEquals(packet, DiscoveryPacket.decode(fromDebug, SyncIdentity.DEBUG))
        assertEquals(packet, DiscoveryPacket.decode(fromRelease, SyncIdentity.RELEASE))
    }
}
