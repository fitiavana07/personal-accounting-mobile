package dev.fitiavana.accounting

import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCacheDao
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.data.network.ExchangeRateFetcher
import dev.fitiavana.accounting.data.network.StockPriceFetcher
import dev.fitiavana.accounting.data.network.StockQuote
import dev.fitiavana.accounting.data.network.TickerQuote
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
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
    private val usd = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
    private val noIdInstrument = Instrument(code = "SOL", note = "", type = "cryptocurrency", decimalPlaces = 9, coingeckoId = null)
    private val nvda = Instrument(code = "NVDA", note = "", type = "stock", decimalPlaces = 2)
    private val brkb = Instrument(code = "BRKB", note = "", type = "stock", decimalPlaces = 2, stockApiSymbol = "BRK-B")

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

    @Test
    fun `stock quote in matching currency upserts cache and reports success`() {
        val stockFetcher = object : StockPriceFetcher {
            override fun fetchQuote(symbol: String): StockQuote {
                assertEquals("NVDA", symbol)
                return StockQuote(symbol = "NVDA", currency = "USD", price = 123.45)
            }
        }
        val repository = ExchangeRateRepository(cacheDao, mock(), stockFetcher)

        val result = repository.refresh(listOf(nvda to usd))

        assertEquals(listOf("NVDA"), result.succeeded)
        assertTrue(result.failed.isEmpty())

        val captor = argumentCaptor<ExchangeRateCache>()
        verify(cacheDao).upsert(captor.capture())
        assertEquals("NVDA:USD", captor.firstValue.pairKey)
        assertEquals(123.45, captor.firstValue.rate, 0.0001)
    }

    @Test
    fun `stock quote uses stockApiSymbol when set instead of code`() {
        val stockFetcher = object : StockPriceFetcher {
            override fun fetchQuote(symbol: String): StockQuote {
                assertEquals("BRK-B", symbol)
                return StockQuote(symbol = "BRK-B", currency = "USD", price = 450.0)
            }
        }
        val repository = ExchangeRateRepository(cacheDao, mock(), stockFetcher)

        val result = repository.refresh(listOf(brkb to usd))

        assertEquals(listOf("BRKB"), result.succeeded)
    }

    @Test
    fun `stock quote in mismatched currency is converted via FX rate`() {
        val xiaomi = Instrument(code = "1810", note = "", type = "stock", decimalPlaces = 2, stockApiSymbol = "1810.HK")
        val stockFetcher = object : StockPriceFetcher {
            override fun fetchQuote(symbol: String): StockQuote = when (symbol) {
                "1810.HK" -> StockQuote(symbol = "1810.HK", currency = "HKD", price = 15.0)
                "HKDUSD=X" -> StockQuote(symbol = "HKDUSD=X", currency = "USD", price = 0.128)
                else -> throw AssertionError("unexpected symbol $symbol")
            }
        }
        val repository = ExchangeRateRepository(cacheDao, mock(), stockFetcher)

        val result = repository.refresh(listOf(xiaomi to usd))

        assertEquals(listOf("1810"), result.succeeded)
        assertTrue(result.failed.isEmpty())

        val captor = argumentCaptor<ExchangeRateCache>()
        verify(cacheDao).upsert(captor.capture())
        assertEquals("1810:USD", captor.firstValue.pairKey)
        assertEquals(1.92, captor.firstValue.rate, 0.0001)
    }

    @Test
    fun `FX rate is fetched once and reused across accounts sharing the same conversion`() {
        val xiaomi = Instrument(code = "1810", note = "", type = "stock", decimalPlaces = 2, stockApiSymbol = "1810.HK")
        var fxFetchCount = 0
        val stockFetcher = object : StockPriceFetcher {
            override fun fetchQuote(symbol: String): StockQuote = when (symbol) {
                "1810.HK" -> StockQuote(symbol = "1810.HK", currency = "HKD", price = 15.0)
                "HKDUSD=X" -> {
                    fxFetchCount++
                    StockQuote(symbol = "HKDUSD=X", currency = "USD", price = 0.128)
                }
                else -> throw AssertionError("unexpected symbol $symbol")
            }
        }
        val repository = ExchangeRateRepository(cacheDao, mock(), stockFetcher)

        val result = repository.refresh(listOf(xiaomi to usd, xiaomi to usd))

        assertEquals(1, fxFetchCount)
        assertEquals(listOf("1810", "1810"), result.succeeded)
    }

    @Test
    fun `FX fetch failure is reported as failed with error message`() {
        val xiaomi = Instrument(code = "1810", note = "", type = "stock", decimalPlaces = 2, stockApiSymbol = "1810.HK")
        val stockFetcher = object : StockPriceFetcher {
            override fun fetchQuote(symbol: String): StockQuote = when (symbol) {
                "1810.HK" -> StockQuote(symbol = "1810.HK", currency = "HKD", price = 15.0)
                "HKDUSD=X" -> throw java.io.IOException("fx timeout")
                else -> throw AssertionError("unexpected symbol $symbol")
            }
        }
        val repository = ExchangeRateRepository(cacheDao, mock(), stockFetcher)

        val result = repository.refresh(listOf(xiaomi to usd))

        assertTrue(result.succeeded.isEmpty())
        assertEquals(listOf("1810"), result.failed)
        assertEquals("fx timeout", result.error)
    }

    @Test
    fun `stock network failure reports pair as failed with error message`() {
        val stockFetcher = object : StockPriceFetcher {
            override fun fetchQuote(symbol: String): StockQuote = throw java.io.IOException("timeout")
        }
        val repository = ExchangeRateRepository(cacheDao, mock(), stockFetcher)

        val result = repository.refresh(listOf(nvda to usd))

        assertTrue(result.succeeded.isEmpty())
        assertEquals(listOf("NVDA"), result.failed)
        assertEquals("timeout", result.error)
    }

    @Test
    fun `one stock quote request covers multiple accounts of same symbol`() {
        var fetchCount = 0
        val stockFetcher = object : StockPriceFetcher {
            override fun fetchQuote(symbol: String): StockQuote {
                fetchCount++
                return StockQuote(symbol = "NVDA", currency = "USD", price = 123.45)
            }
        }
        val repository = ExchangeRateRepository(cacheDao, mock(), stockFetcher)

        val result = repository.refresh(listOf(nvda to usd, nvda to usd))

        assertEquals(1, fetchCount)
        assertEquals(listOf("NVDA", "NVDA"), result.succeeded)
    }

    @Test
    fun `mixed crypto and stock pairs are refreshed independently`() {
        val fetcher = object : ExchangeRateFetcher {
            override fun fetchTickers(coinId: String, exchangeId: String): List<TickerQuote> =
                listOf(TickerQuote(target = "USDT", exchangeIdentifier = "binance", last = 65000.0))
        }
        val stockFetcher = object : StockPriceFetcher {
            override fun fetchQuote(symbol: String): StockQuote =
                StockQuote(symbol = "NVDA", currency = "USD", price = 123.45)
        }
        val repository = ExchangeRateRepository(cacheDao, fetcher, stockFetcher)

        val result = repository.refresh(listOf(btc to usdt, nvda to usd))

        assertEquals(listOf("BTC", "NVDA"), result.succeeded.sorted())
    }
}
