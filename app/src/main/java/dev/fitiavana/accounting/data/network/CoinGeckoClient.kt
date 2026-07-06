package dev.fitiavana.accounting.data.network

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Talks to CoinGecko's public `/coins/{id}/tickers` endpoint. Runs synchronously —
 * callers must invoke this off the main thread.
 */
interface ExchangeRateFetcher {
    fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote>
}

class CoinGeckoClient(private val client: OkHttpClient = buildClient()) : ExchangeRateFetcher {

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

    companion object {
        private fun buildClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)

            // API 19-21 ship SSLSocket with TLSv1.2 support but disabled by default;
            // api.coingecko.com requires TLSv1.2+ and rejects the handshake otherwise.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trustManagerFactory.init(null as KeyStore?)
                val trustManager = trustManagerFactory.trustManagers[0] as X509TrustManager

                val sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, arrayOf(trustManager), null)

                builder.sslSocketFactory(Tls12SocketFactory(sslContext.socketFactory), trustManager)
                builder.connectionSpecs(
                    listOf(
                        ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                            .tlsVersions(TlsVersion.TLS_1_2)
                            .build(),
                        ConnectionSpec.COMPATIBLE_TLS
                    )
                )
            }

            return builder.build()
        }
    }
}
