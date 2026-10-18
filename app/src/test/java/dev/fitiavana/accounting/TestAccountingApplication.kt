package dev.fitiavana.accounting

import okhttp3.OkHttpClient
import java.io.IOException

/**
 * The application every Robolectric test runs under (see `robolectric.properties`). Its HTTP
 * client fails every request before it connects, so no test can reach a real exchange through
 * [AppContainer]. Tests that need responses build their own fetchers around a fake instead.
 */
class TestAccountingApplication : AccountingApplication() {
    override fun onCreate() {
        AppContainer.httpClientFactory = ::offlineClient
        super.onCreate()
    }

    private fun offlineClient() = OkHttpClient.Builder()
        .addInterceptor { chain -> throw IOException("Network is disabled in unit tests: ${chain.request().url()}") }
        .build()
}
