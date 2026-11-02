package dev.fitiavana.accounting.network.evm

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Builds `eth_call` requests and reads their answers. Pure, no I/O. */
object JsonRpc {
    fun ethCallBody(to: String, data: String): String =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "eth_call")
            .put(
                "params",
                JSONArray()
                    .put(JSONObject().put("to", to).put("data", data))
                    .put("latest")
            )
            .toString()

    /** The hex `result`, or an [RpcException] when the node answered with an error or something unreadable. */
    fun parseResult(json: String): String {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw RpcException.Malformed("not JSON", e)
        }
        root.optJSONObject("error")?.let { throw RpcException.Rpc(it.optString("message", "unknown error")) }
        return root.opt("result") as? String ?: throw RpcException.Malformed("no result", null)
    }
}
