package dev.fitiavana.accounting.network.evm

import dev.fitiavana.accounting.network.Api19HttpClients
import java.io.IOException
import java.math.BigDecimal
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

data class TokenInfo(val symbol: String, val decimals: Int)

/** Reads ERC-20 data on Base. Synchronous, call off the main thread. Failures are [RpcException]s where known. */
interface Erc20BalanceFetcher {
    /** [holder]'s balance of [token] in token units (the raw amount scaled by [decimals]). */
    @Throws(IOException::class)
    fun fetchBalance(holder: String, token: String, decimals: Int): BigDecimal

    @Throws(IOException::class)
    fun fetchTokenInfo(token: String): TokenInfo
}

/** Public Base RPC over HTTPS JSON-RPC. Transient failures are retried by [RetryPolicy]. */
class HttpErc20BalanceFetcher(
    private val client: OkHttpClient = Api19HttpClients.build(),
    private val url: String = BASE_RPC_URL,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) }
) : Erc20BalanceFetcher {

    @Throws(IOException::class)
    override fun fetchBalance(holder: String, token: String, decimals: Int): BigDecimal =
        Erc20Codec.scale(call(token, Erc20Codec.balanceOfData(holder)) { Erc20Codec.parseUint(it) }, decimals)

    @Throws(IOException::class)
    override fun fetchTokenInfo(token: String): TokenInfo = TokenInfo(
        call(token, Erc20Codec.SYMBOL_DATA) { Erc20Codec.parseSymbol(it) },
        call(token, Erc20Codec.DECIMALS_DATA) { Erc20Codec.parseDecimals(it) }
    )

    private fun <T> call(to: String, data: String, decode: (String) -> T): T {
        val result = RetryPolicy.run(sleep) { post(JsonRpc.ethCallBody(to, data)) }
        return try {
            decode(result)
        } catch (e: IllegalArgumentException) {
            throw RpcException.Malformed(e.message ?: "bad result", e)
        } catch (e: ArithmeticException) {
            throw RpcException.Malformed("bad result", e)
        } catch (e: IndexOutOfBoundsException) {
            throw RpcException.Malformed("bad result", e)
        }
    }

    private fun post(json: String): String {
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json)
        val request = Request.Builder().url(url).post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw RpcException.HttpStatus(response.code())
                val text = response.body()?.string() ?: throw RpcException.Malformed("empty body", null)
                return JsonRpc.parseResult(text)
            }
        } catch (e: SocketTimeoutException) {
            throw RpcException.Timeout(e)
        } catch (e: UnknownHostException) {
            throw RpcException.NoNetwork(e)
        } catch (e: ConnectException) {
            throw RpcException.NoNetwork(e)
        } catch (e: NoRouteToHostException) {
            throw RpcException.NoNetwork(e)
        }
    }

    companion object {
        const val BASE_RPC_URL = "https://mainnet.base.org"
    }
}
