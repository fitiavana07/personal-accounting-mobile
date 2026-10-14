package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.FormatException
import dev.fitiavana.accounting.features.int
import dev.fitiavana.accounting.features.obj
import dev.fitiavana.accounting.features.string
import org.json.JSONException
import org.json.JSONObject

/** What two devices say to each other during a session. */
sealed interface SyncMessage {
    /** The initiator's whole state. */
    data class Request(val peer: PeerInfo, val snapshot: SyncSnapshot) : SyncMessage

    /** The responder's state after merging the request, so the initiator ends up with the merge. */
    data class Response(val peer: PeerInfo, val snapshot: SyncSnapshot) : SyncMessage

    /** The responder could not use the request. */
    data class Failure(val message: String) : SyncMessage
}

object SyncMessageJson {
    sealed interface DecodeResult {
        data class Success(val message: SyncMessage) : DecodeResult

        /** Another protocol version: nothing else in the message is read. */
        data class Incompatible(val version: Int) : DecodeResult

        data class Invalid(val message: String) : DecodeResult
    }

    fun encode(message: SyncMessage): String = JSONObject().apply {
        put("protocol", PROTOCOL_VERSION)
        when (message) {
            is SyncMessage.Request -> putPeerAndSnapshot("request", message.peer, message.snapshot)
            is SyncMessage.Response -> putPeerAndSnapshot("response", message.peer, message.snapshot)
            is SyncMessage.Failure -> put("type", "failure").put("message", message.message)
        }
    }.toString()

    fun decode(json: String): DecodeResult = try {
        val root = JSONObject(json)
        val version = root.int("protocol")
        if (version != PROTOCOL_VERSION) DecodeResult.Incompatible(version) else DecodeResult.Success(parse(root))
    } catch (e: FormatException) {
        DecodeResult.Invalid(e.message.orEmpty())
    } catch (e: JSONException) {
        DecodeResult.Invalid("The message is not valid JSON")
    }

    private fun JSONObject.putPeerAndSnapshot(type: String, peer: PeerInfo, snapshot: SyncSnapshot) {
        put("type", type)
        put("deviceId", peer.deviceId)
        put("name", peer.name)
        put("snapshot", SyncJson.encode(snapshot))
    }

    private fun parse(root: JSONObject): SyncMessage = when (val type = root.string("type")) {
        "request" -> SyncMessage.Request(root.peer(), SyncJson.decode(root.obj("snapshot")))
        "response" -> SyncMessage.Response(root.peer(), SyncJson.decode(root.obj("snapshot")))
        "failure" -> SyncMessage.Failure(root.string("message"))
        else -> throw FormatException("Unknown message type '$type'")
    }

    private fun JSONObject.peer() = PeerInfo(string("deviceId"), string("name"))
}
