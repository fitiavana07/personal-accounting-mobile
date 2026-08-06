package dev.fitiavana.accounting.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Talks to Yahoo Finance's public `/v8/finance/chart/{symbol}` endpoint, which covers both
 * US-listed tickers (e.g. `NVDA`, `BRK-B`) and other exchanges via a suffix (e.g. `1810.HK`
 * for Xiaomi on the Hong Kong exchange). Runs synchronously — callers must invoke this off
 * the main thread.
 */
interface StockPriceFetcher {
    fun fetchQuote(symbol: String): StockQuote
}

class YahooFinanceClient(private val client: OkHttpClient = Api19HttpClients.build()) : StockPriceFetcher {

    @Throws(IOException::class)
    override fun fetchQuote(symbol: String): StockQuote {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Yahoo Finance request failed: HTTP ${response.code()}")
            }
            val body = response.body()?.string() ?: throw IOException("Empty Yahoo Finance response body")
            return YahooFinanceResponseParser.parseQuote(body)
                ?: throw IOException("No quote in Yahoo Finance response for $symbol")
        }
    }
}
