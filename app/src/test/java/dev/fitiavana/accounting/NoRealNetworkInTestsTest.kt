package dev.fitiavana.accounting

import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.features.instruments.Instrument
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Collections

/**
 * The container the screens are built from must not reach the internet under test: a unit test
 * that talks to p2p.binance.com (or any exchange) is slow, flaky and hammers a third party.
 * OkHttp asks the default [ProxySelector] for every URL it is about to connect to, so recording
 * those requests detects an attempt without needing a network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class NoRealNetworkInTestsTest {
    private val requestedHosts: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private lateinit var original: ProxySelector

    @Before
    fun recordConnectionAttempts() {
        original = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> {
                requestedHosts.add(uri.host)
                return listOf(Proxy.NO_PROXY)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
        })
    }

    @After
    fun restore() = ProxySelector.setDefault(original)

    private val container get() = AppContainer.getInstance(ApplicationProvider.getApplicationContext())

    @Test
    fun `fetching P2P prices and payment methods stays offline`() {
        container.p2pPriceRepository.fetch()
        container.p2pPriceRepository.getPaymentMethods()

        assertEquals(emptyList<String>(), requestedHosts.toList())
    }

    @Test
    fun `fetching CEX prices stays offline`() {
        container.cexPriceRepository.fetchAll("BTC", "USDT")

        assertEquals(emptyList<String>(), requestedHosts.toList())
    }

    @Test
    fun `refreshing exchange rates stays offline`() {
        val usdt = Instrument("USDT", "Tether", Instrument.TYPE_CRYPTOCURRENCY)
        val btc = Instrument("BTC", "Bitcoin", Instrument.TYPE_CRYPTOCURRENCY, coingeckoId = "bitcoin")
        val stock = Instrument("NVDA", "Nvidia", "stock")

        // The cache upsert goes through Room, which refuses the main thread.
        Thread { container.exchangeRateRepository.refresh(listOf(btc to usdt, stock to usdt)) }
            .apply { start() }
            .join()

        assertEquals(emptyList<String>(), requestedHosts.toList())
    }
}
