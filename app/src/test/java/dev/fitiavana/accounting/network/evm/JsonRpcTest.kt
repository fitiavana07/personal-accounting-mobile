package dev.fitiavana.accounting.network.evm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class JsonRpcTest {

    @Test
    fun `eth_call body targets the contract at the latest block`() {
        val body = JSONObject(JsonRpc.ethCallBody("0xabc", "0x1234"))

        assertEquals("2.0", body.getString("jsonrpc"))
        assertEquals("eth_call", body.getString("method"))
        val params = body.getJSONArray("params")
        assertEquals("0xabc", params.getJSONObject(0).getString("to"))
        assertEquals("0x1234", params.getJSONObject(0).getString("data"))
        assertEquals("latest", params.getString(1))
    }

    @Test
    fun `parses the result of a successful answer`() {
        val json = """{"jsonrpc":"2.0","id":1,"result":"0x0f"}"""

        assertEquals("0x0f", JsonRpc.parseResult(json))
    }

    @Test
    fun `an error object becomes an Rpc exception with its message`() {
        val json = """{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"execution reverted"}}"""

        try {
            JsonRpc.parseResult(json)
            fail("expected an exception")
        } catch (e: RpcException.Rpc) {
            assertEquals("execution reverted", e.rpcMessage)
        }
    }

    @Test(expected = RpcException.Malformed::class)
    fun `an answer that is not json is malformed`() {
        JsonRpc.parseResult("<html>Bad gateway</html>")
    }

    @Test(expected = RpcException.Malformed::class)
    fun `an answer without a result is malformed`() {
        JsonRpc.parseResult("""{"jsonrpc":"2.0","id":1}""")
    }

    @Test(expected = RpcException.Malformed::class)
    fun `a result that is not a string is malformed`() {
        JsonRpc.parseResult("""{"jsonrpc":"2.0","id":1,"result":42}""")
    }
}
