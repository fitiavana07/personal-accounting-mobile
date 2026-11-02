package dev.fitiavana.accounting.network.evm

import java.io.IOException
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class HttpErc20BalanceFetcherTest {

    private val vault = "0xB99B6dF96d4d5448cC0a5B3e0ef7896df9507Cf5"
    private val usdc = Erc20Codec.BASE_USDC

    private class Fake(private val answer: (Request) -> Response) : Interceptor {
        val requests = mutableListOf<Request>()
        override fun intercept(chain: Interceptor.Chain): Response {
            requests += chain.request()
            return answer(chain.request())
        }
    }

    private fun reply(request: Request, code: Int, body: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("m")
        .body(ResponseBody.create(MediaType.parse("application/json"), body)).build()

    private fun result(hex: String) = """{"jsonrpc":"2.0","id":1,"result":"$hex"}"""

    private fun fetcher(fake: Fake, sleeps: MutableList<Long> = mutableListOf()) = HttpErc20BalanceFetcher(
        client = OkHttpClient.Builder().addInterceptor(fake).build(),
        sleep = { sleeps += it }
    )

    private fun bodyOf(request: Request): JSONObject {
        val buffer = Buffer()
        request.body()!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `reads a balance and scales it by the decimals`() {
        val fake = Fake { reply(it, 200, result("0x" + "0".repeat(56) + "000f4240")) }

        val balance = fetcher(fake).fetchBalance(vault, usdc, 6)

        assertEquals(0, BigDecimal("1").compareTo(balance))
        val request = fake.requests.single()
        assertEquals(HttpErc20BalanceFetcher.BASE_RPC_URL + "/", request.url().toString())
        assertEquals("POST", request.method())
        val call = bodyOf(request).getJSONArray("params").getJSONObject(0)
        assertEquals(usdc, call.getString("to"))
        assertEquals(Erc20Codec.balanceOfData(vault), call.getString("data"))
    }

    @Test
    fun `reads a token symbol and decimals`() {
        val symbol = "0x" + "20".padStart(64, '0') + "4".padStart(64, '0') + "55534443".padEnd(64, '0')
        val fake = Fake { request ->
            val data = bodyOf(request).getJSONArray("params").getJSONObject(0).getString("data")
            reply(request, 200, result(if (data == Erc20Codec.DECIMALS_DATA) "0x" + "0".repeat(62) + "06" else symbol))
        }

        assertEquals(TokenInfo("USDC", 6), fetcher(fake).fetchTokenInfo(usdc))
    }

    @Test
    fun `an address without a contract is reported as malformed`() {
        val fake = Fake { reply(it, 200, result("0x")) }

        try {
            fetcher(fake).fetchTokenInfo(vault)
            fail("expected an exception")
        } catch (e: RpcException.Malformed) {
            assertTrue(e.message!!.contains("contract"))
        }
    }

    @Test
    fun `an unknown host is no network and is not retried`() {
        val fake = Fake { throw UnknownHostException("mainnet.base.org") }

        try {
            fetcher(fake).fetchBalance(vault, usdc, 6)
            fail("expected an exception")
        } catch (e: RpcException.NoNetwork) {
            // expected
        }
        assertEquals(1, fake.requests.size)
    }

    @Test
    fun `a timeout is retried twice with backoff then reported`() {
        val fake = Fake { throw SocketTimeoutException("timeout") }
        val sleeps = mutableListOf<Long>()

        try {
            fetcher(fake, sleeps).fetchBalance(vault, usdc, 6)
            fail("expected an exception")
        } catch (e: RpcException.Timeout) {
            // expected
        }
        assertEquals(3, fake.requests.size)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
    }

    @Test
    fun `a 503 is retried and the next answer is used`() {
        var calls = 0
        val fake = Fake {
            if (++calls == 1) reply(it, 503, "") else reply(it, 200, result("0x" + "0".repeat(64)))
        }

        val balance = fetcher(fake).fetchBalance(vault, usdc, 6)

        assertEquals(0, BigDecimal.ZERO.compareTo(balance))
        assertEquals(2, fake.requests.size)
    }

    @Test
    fun `a 404 is reported with its code and not retried`() {
        val fake = Fake { reply(it, 404, "") }

        try {
            fetcher(fake).fetchBalance(vault, usdc, 6)
            fail("expected an exception")
        } catch (e: RpcException.HttpStatus) {
            assertEquals(404, e.code)
        }
        assertEquals(1, fake.requests.size)
    }

    @Test
    fun `an rpc error is reported and not retried`() {
        val fake = Fake { reply(it, 200, """{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"reverted"}}""") }

        try {
            fetcher(fake).fetchBalance(vault, usdc, 6)
            fail("expected an exception")
        } catch (e: RpcException.Rpc) {
            assertEquals("reverted", e.rpcMessage)
        }
        assertEquals(1, fake.requests.size)
    }

    @Test
    fun `other io failures pass through unchanged`() {
        val fake = Fake { throw IOException("boom") }

        try {
            fetcher(fake).fetchBalance(vault, usdc, 6)
            fail("expected an exception")
        } catch (e: RpcException) {
            fail("must not be wrapped")
        } catch (e: IOException) {
            assertEquals("boom", e.message)
        }
    }
}
