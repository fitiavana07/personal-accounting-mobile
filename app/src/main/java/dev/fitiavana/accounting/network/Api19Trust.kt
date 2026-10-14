package dev.fitiavana.accounting.network

import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Trust store for API 19-20 devices, whose system CA store predates roots that modern hosts
 * now chain to (e.g. Yahoo Finance and Binance P2P use DigiCert Global Root G2). The system
 * roots stay trusted; the bundled ones are added on top.
 */
object Api19Trust {
    private val BUNDLED_ROOT_RESOURCES = listOf("certs/digicert_global_root_g2.pem")

    fun bundledRoots(): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        return BUNDLED_ROOT_RESOURCES.map { path ->
            val stream = Api19Trust::class.java.classLoader!!.getResourceAsStream(path)
                ?: throw IllegalStateException("Missing bundled certificate $path")
            stream.use { factory.generateCertificate(it) as X509Certificate }
        }
    }

    fun trustManager(): X509TrustManager {
        val system = trustManagerFor(null)
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            system.acceptedIssuers.forEachIndexed { i, cert -> setCertificateEntry("system-$i", cert) }
            bundledRoots().forEachIndexed { i, cert -> setCertificateEntry("bundled-$i", cert) }
        }
        return trustManagerFor(keyStore)
    }

    private fun trustManagerFor(keyStore: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
