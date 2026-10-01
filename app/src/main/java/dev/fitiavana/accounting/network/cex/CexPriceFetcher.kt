package dev.fitiavana.accounting.network.cex

import dev.fitiavana.accounting.network.Api19HttpClients
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Fetches the spot price of base/quote on one exchange. Synchronous — call off the main thread. */
interface CexPriceFetcher {
    @Throws(IOException::class)
    fun fetchPrice(base: String, quote: String): Double
}

class HttpCexPriceFetcher(
    private val endpoint: CexEndpoint,
    private val client: OkHttpClient = Api19HttpClients.build()
) : CexPriceFetcher {

    @Throws(IOException::class)
    override fun fetchPrice(base: String, quote: String): Double {
        val request = Request.Builder().url(endpoint.url(base, quote)).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body()?.string()
            if (!response.isSuccessful) {
                // Exchanges answer 400 for unknown symbols.
                if (response.code() == 400) throw PairNotListedException("HTTP 400")
                throw IOException("Request failed: HTTP ${response.code()}")
            }
            return endpoint.parse(body ?: throw IOException("Empty response body"))
        }
    }

    companion object {
        /** One fetcher per exchange, sharing a single HTTP client. */
        fun createAll(client: OkHttpClient = Api19HttpClients.build()): Map<CexId, CexPriceFetcher> =
            CexEndpoints.all.mapValues { (_, endpoint) -> HttpCexPriceFetcher(endpoint, client) }
    }
}
