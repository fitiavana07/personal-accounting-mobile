package dev.fitiavana.accounting.network

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

/**
 * Builds an [OkHttpClient] that works against modern HTTPS endpoints on API 19-20 devices,
 * which ship SSLSocket with TLSv1.2 support but disabled by default.
 */
object Api19HttpClients {
    fun build(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val trustManager = Api19Trust.trustManager()

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
