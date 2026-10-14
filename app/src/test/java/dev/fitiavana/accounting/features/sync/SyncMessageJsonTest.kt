package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.sync.SyncFixtures.account
import dev.fitiavana.accounting.features.sync.SyncFixtures.data
import dev.fitiavana.accounting.features.sync.SyncFixtures.key
import dev.fitiavana.accounting.features.sync.SyncFixtures.live
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncMessageJsonTest {
    private val peer = PeerInfo("dev-a", "Strategic Banana")
    private val snapshot = SyncSnapshot(
        data(accounts = listOf(account("a1"))),
        mapOf(key(SyncKind.ACCOUNT, "a1") to live(1_790_000_000_000, "dev-a")),
    )

    private fun decoded(json: String) = (SyncMessageJson.decode(json) as SyncMessageJson.DecodeResult.Success).message

    @Test
    fun `requests and responses round trip`() {
        val request = SyncMessage.Request(peer, snapshot)
        val response = SyncMessage.Response(peer, snapshot)

        assertEquals(request, decoded(SyncMessageJson.encode(request)))
        assertEquals(response, decoded(SyncMessageJson.encode(response)))
    }

    @Test
    fun `a failure round trips`() {
        val failure = SyncMessage.Failure("The other device uses database version 19")

        assertEquals(failure, decoded(SyncMessageJson.encode(failure)))
    }

    @Test
    fun `the protocol version travels in every message`() {
        val json = JSONObject(SyncMessageJson.encode(SyncMessage.Failure("x")))

        assertEquals(PROTOCOL_VERSION, json.getInt("protocol"))
    }

    @Test
    fun `another protocol version is reported before anything else is read`() {
        val result = SyncMessageJson.decode("""{"protocol": 99, "type": "something new"}""")

        assertEquals(SyncMessageJson.DecodeResult.Incompatible(99), result)
    }

    @Test
    fun `malformed messages are invalid`() {
        fun invalid(json: String) = SyncMessageJson.decode(json) is SyncMessageJson.DecodeResult.Invalid

        assertTrue(invalid("nope"))
        assertTrue(invalid("""{"type": "request"}"""))
        assertTrue(invalid("""{"protocol": 1, "type": "unknown"}"""))
        assertTrue(invalid("""{"protocol": 1, "type": "request", "deviceId": "a"}"""))
        assertTrue(invalid("""{"protocol": "1", "type": "request"}"""))
    }

    @Test
    fun `a bad snapshot inside a message is invalid`() {
        val json = JSONObject(SyncMessageJson.encode(SyncMessage.Request(peer, snapshot))).apply {
            getJSONObject("snapshot").remove("meta")
        }

        val result = SyncMessageJson.decode(json.toString()) as SyncMessageJson.DecodeResult.Invalid
        assertTrue(result.message.contains("meta"))
    }
}
