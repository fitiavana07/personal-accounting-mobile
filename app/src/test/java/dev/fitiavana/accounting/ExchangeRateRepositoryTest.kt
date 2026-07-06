package dev.fitiavana.accounting

import dev.fitiavana.accounting.data.dao.ExchangeRateCacheDao
import dev.fitiavana.accounting.data.model.ExchangeRateCache
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.network.ExchangeRateFetcher
import dev.fitiavana.accounting.data.network.TickerQuote
import dev.fitiavana.accounting.data.repository.ExchangeRateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ExchangeRateRepositoryTest {

    private lateinit var cacheDao: ExchangeRateCacheDao
    private val btc = Instrument(code = "BTC", note = "", type = "cryptocurrency", decimalPlaces = 8, coingeckoId = "bitcoin")
    private val eth = Instrument(code = "ETH", note = "", type = "cryptocurrency", decimalPlaces = 18, coingeckoId = "ethereum")
    private val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)
    private val noIdInstrument = Instrument(code = "SOL", note = "", type = "cryptocurrency", decimalPlaces = 9, coingeckoId = null)

    @Before
    fun setUp() {
        cacheDao = mock()
    }

    @Test
    fun `successful fetch upserts cache and reports success`() {
        val fetcher = object : ExchangeRateFetcher {
            override fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote> {
                assertEquals("bitcoin", coinId)
                assertEquals("binance", exchangeId)
                return listOf(TickerQuote(target = "USDT", exchangeIdentifier = "binance", last = 65000.0))
            }
        }
        val repository = ExchangeRateRepository(cacheDao, fetcher)

        val result = repository.refresh(listOf(btc to usdt))

        assertEquals(listOf("BTC"), result.succeeded)
        assertTrue(result.failed.isEmpty())
        assertEquals(null, result.error)

        val captor = argumentCaptor<ExchangeRateCache>()
        verify(cacheDao).upsert(captor.capture())
        assertEquals("BTC:USDT", captor.firstValue.pairKey)
        assertEquals(65000.0, captor.firstValue.rate, 0.0001)
    }

    @Test
    fun `target matching is case insensitive`() {
        val fetcher = object : ExchangeRateFetcher {
            override fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote> =
                listOf(TickerQuote(target = "usdt", exchangeIdentifier = "binance", last = 65000.0))
        }
        val repository = ExchangeRateRepository(cacheDao, fetcher)

        val result = repository.refresh(listOf(btc to usdt))

        assertEquals(listOf("BTC"), result.succeeded)
    }

    @Test
    fun `pair missing from ticker list is reported as failed`() {
        val fetcher = object : ExchangeRateFetcher {
            override fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote> = emptyList()
        }
        val repository = ExchangeRateRepository(cacheDao, fetcher)

        val result = repository.refresh(listOf(btc to usdt))

        assertTrue(result.succeeded.isEmpty())
        assertEquals(listOf("BTC"), result.failed)
    }

    @Test
    fun `instrument without coingeckoId is reported as failed without network call`() {
        val fetcher = object : ExchangeRateFetcher {
            override fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote> {
                throw AssertionError("should not be called")
            }
        }
        val repository = ExchangeRateRepository(cacheDao, fetcher)

        val result = repository.refresh(listOf(noIdInstrument to usdt))

        assertTrue(result.succeeded.isEmpty())
        assertEquals(listOf("SOL"), result.failed)
    }

    @Test
    fun `network failure reports pairs for that coin as failed with error message`() {
        val fetcher = object : ExchangeRateFetcher {
            override fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote> {
                throw java.io.IOException("timeout")
            }
        }
        val repository = ExchangeRateRepository(cacheDao, fetcher)

        val result = repository.refresh(listOf(btc to usdt))

        assertTrue(result.succeeded.isEmpty())
        assertEquals(listOf("BTC"), result.failed)
        assertEquals("timeout", result.error)
    }

    @Test
    fun `one request per distinct instrument covers multiple accounts of same coin`() {
        var fetchCount = 0
        val fetcher = object : ExchangeRateFetcher {
            override fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote> {
                fetchCount++
                return listOf(TickerQuote(target = "USDT", exchangeIdentifier = "binance", last = 65000.0))
            }
        }
        val repository = ExchangeRateRepository(cacheDao, fetcher)

        val result = repository.refresh(listOf(btc to usdt, btc to usdt))

        assertEquals(1, fetchCount)
        assertEquals(listOf("BTC", "BTC"), result.succeeded)
    }

    @Test
    fun `failure fetching one coin does not block another coin`() {
        val fetcher = object : ExchangeRateFetcher {
            override fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote> {
                if (coinId == "bitcoin") throw java.io.IOException("timeout")
                return listOf(TickerQuote(target = "USDT", exchangeIdentifier = "binance", last = 3200.0))
            }
        }
        val repository = ExchangeRateRepository(cacheDao, fetcher)

        val result = repository.refresh(listOf(btc to usdt, eth to usdt))

        assertEquals(listOf("ETH"), result.succeeded)
        assertEquals(listOf("BTC"), result.failed)
    }

    @Test
    fun `empty pairs list does not call fetcher`() {
        val fetcher = object : ExchangeRateFetcher {
            override fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote> {
                throw AssertionError("should not be called")
            }
        }
        val repository = ExchangeRateRepository(cacheDao, fetcher)

        val result = repository.refresh(emptyList())

        assertTrue(result.succeeded.isEmpty())
        assertTrue(result.failed.isEmpty())
    }
}
