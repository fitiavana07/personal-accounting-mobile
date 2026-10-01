package dev.fitiavana.accounting.network.p2p

import dev.fitiavana.accounting.network.Api19HttpClients
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException

/** Talks to Binance P2P. Synchronous — call off the main thread. */
interface P2pPriceFetcher {
    /** The best ads for one side, optionally only those accepting [payType] (a payment method identifier). */
    @Throws(IOException::class)
    fun fetchTopPrices(side: P2pSide, payType: String? = null): List<P2pAd>

    @Throws(IOException::class)
    fun fetchPaymentMethods(): List<P2pPaymentMethod>
}

class HttpP2pPriceFetcher(
    private val fiat: String = "MGA",
    private val asset: String = "USDT",
    private val rows: Int = 3,
    private val client: OkHttpClient = Api19HttpClients.build()
) : P2pPriceFetcher {

    @Throws(IOException::class)
    override fun fetchTopPrices(side: P2pSide, payType: String?): List<P2pAd> =
        BinanceP2pApi.parseAds(post(BinanceP2pApi.URL, BinanceP2pApi.requestBody(fiat, asset, side, rows, payType)))
            .take(rows)

    @Throws(IOException::class)
    override fun fetchPaymentMethods(): List<P2pPaymentMethod> =
        BinanceP2pApi.parsePaymentMethods(
            post(BinanceP2pApi.FILTER_CONDITIONS_URL, BinanceP2pApi.filterConditionsBody(fiat))
        )

    private fun post(url: String, json: String): String {
        val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json)
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Request failed: HTTP ${response.code()}")
            return response.body()?.string() ?: throw IOException("Empty response body")
        }
    }
}
