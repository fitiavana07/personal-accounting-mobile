package dev.fitiavana.accounting.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Talks to CoinGecko's public `/coins/{id}/tickers` endpoint. Runs synchronously —
 * callers must invoke this off the main thread.
 */
interface ExchangeRateFetcher {
    fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote>
}

class CoinGeckoClient(private val client: OkHttpClient = Api19HttpClients.build()) : ExchangeRateFetcher {

    @Throws(IOException::class)
    override fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote> {
        val url = "https://api.coingecko.com/api/v3/coins/$coinId/tickers?exchange_ids=$exchangeId"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("CoinGecko request failed: HTTP ${response.code()}")
            }
            val body = response.body()?.string() ?: throw IOException("Empty CoinGecko response body")
            return CoinGeckoResponseParser.parseTickers(body)
        }
    }
}
