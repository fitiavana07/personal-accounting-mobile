package dev.fitiavana.accounting.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.data.dao.ExchangeRateCacheDao
import dev.fitiavana.accounting.data.model.ExchangeRateCache
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.network.CoinGeckoClient
import dev.fitiavana.accounting.data.network.ExchangeRateFetcher
import dev.fitiavana.accounting.data.network.StockPriceFetcher
import dev.fitiavana.accounting.data.network.YahooFinanceClient
import java.io.IOException

data class RefreshResult(
    val succeeded: List<String>,
    val failed: List<String>,
    val error: String?
)

class ExchangeRateRepository(
    private val cacheDao: ExchangeRateCacheDao,
    private val fetcher: ExchangeRateFetcher = CoinGeckoClient(),
    private val stockFetcher: StockPriceFetcher = YahooFinanceClient()
) {
    fun getAllCached(): LiveData<List<ExchangeRateCache>> = cacheDao.getAll()

    fun getAllCachedSync(): List<ExchangeRateCache> = cacheDao.getAllSync()

    /**
     * Fetches current rates for each (instrument, intermediaryInstrument) pair and
     * upserts them into the cache. Runs synchronously — callers must invoke this off
     * the main thread. Cryptocurrency pairs come from CoinGecko, stock pairs from Yahoo
     * Finance; everything else is reported as failed without hitting the network.
     */
    fun refresh(pairs: List<Pair<Instrument, Instrument>>): RefreshResult {
        val (stockPairs, cryptoPairs) = pairs.partition { it.first.type == "stock" }
        val cryptoResult = refreshCrypto(cryptoPairs)
        val stockResult = refreshStocks(stockPairs)
        return RefreshResult(
            succeeded = cryptoResult.succeeded + stockResult.succeeded,
            failed = cryptoResult.failed + stockResult.failed,
            error = cryptoResult.error ?: stockResult.error
        )
    }

    /**
     * Rates come from CoinGecko's `/coins/{id}/tickers` endpoint, which returns the
     * actual exchange-quoted price for a coin against every currency it's traded
     * against on that exchange (e.g. Binance's live BTC/USDT price) — this is the real
     * traded pair, not a synthetic rate derived via an intermediate reference currency.
     * One request is made per distinct instrument (its tickers cover every intermediary
     * that instrument is paired with).
     */
    private fun refreshCrypto(pairs: List<Pair<Instrument, Instrument>>): RefreshResult {
        val eligible = pairs.filter { it.first.coingeckoId != null }
        val ineligible = pairs.filter { it.first.coingeckoId == null }.map { it.first.code }

        if (ineligible.isNotEmpty()) {
            Log.w(TAG, "Skipping rate fetch, no coingeckoId set for: ${ineligible.joinToString(", ")}")
        }

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var error: String? = null
        val now = System.currentTimeMillis()

        for ((coinId, pairsForCoin) in eligible.groupBy { it.first.coingeckoId!! }) {
            val tickers = try {
                fetcher.fetchTickers(coinId, EXCHANGE_ID)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch tickers for $coinId on $EXCHANGE_ID", e)
                error = e.message
                failed.addAll(pairsForCoin.map { it.first.code })
                continue
            }

            val quoteByTarget = tickers.associateBy { it.target.uppercase() }
            for ((instrument, intermediary) in pairsForCoin) {
                val quote = quoteByTarget[intermediary.code.uppercase()]
                if (quote != null) {
                    upsert(instrument, intermediary, quote.last, now)
                    succeeded.add(instrument.code)
                } else {
                    Log.w(
                        TAG,
                        "No $EXCHANGE_ID ticker for ${instrument.code} ($coinId) / ${intermediary.code}"
                    )
                    failed.add(instrument.code)
                }
            }
        }

        return RefreshResult(succeeded, failed + ineligible, error)
    }

    /**
     * Rates come from Yahoo Finance's `/v8/finance/chart/{symbol}` endpoint, which quotes
     * a stock in its native trading currency (e.g. USD for NVDA, HKD for 1810.HK). When
     * that currency differs from the account's intermediary instrument, the quote is
     * converted using a currency-pair quote from the same endpoint (e.g. `HKDUSD=X`) —
     * Yahoo Finance serves FX rates through the identical ticker format, so no separate
     * FX API is needed. Uses [Instrument.stockApiSymbol] when set, since it may differ
     * from the instrument code (e.g. `BRK.B` -> `BRK-B`, or `1810` -> `1810.HK`).
     */
    private fun refreshStocks(pairs: List<Pair<Instrument, Instrument>>): RefreshResult {
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var error: String? = null
        val now = System.currentTimeMillis()
        val fxRates = mutableMapOf<Pair<String, String>, Double>()

        for ((apiSymbol, pairsForSymbol) in pairs.groupBy { it.first.stockApiSymbol ?: it.first.code }) {
            val quote = try {
                stockFetcher.fetchQuote(apiSymbol)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to fetch stock quote for $apiSymbol", e)
                error = e.message
                failed.addAll(pairsForSymbol.map { it.first.code })
                continue
            }

            for ((instrument, intermediary) in pairsForSymbol) {
                val priceInIntermediary = try {
                    convert(quote.price, quote.currency, intermediary.code, fxRates)
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to fetch FX rate ${quote.currency}->${intermediary.code} for ${instrument.code}", e)
                    error = e.message
                    failed.add(instrument.code)
                    continue
                }
                upsert(instrument, intermediary, priceInIntermediary, now)
                succeeded.add(instrument.code)
            }
        }

        return RefreshResult(succeeded, failed, error)
    }

    @Throws(IOException::class)
    private fun convert(
        price: Double,
        fromCurrency: String,
        toCurrency: String,
        fxRates: MutableMap<Pair<String, String>, Double>
    ): Double {
        if (fromCurrency.equals(toCurrency, ignoreCase = true)) return price
        val key = fromCurrency.uppercase() to toCurrency.uppercase()
        val rate = fxRates.getOrPut(key) {
            stockFetcher.fetchQuote("$fromCurrency$toCurrency=X").price
        }
        return price * rate
    }

    private fun upsert(instrument: Instrument, intermediary: Instrument, rate: Double, fetchedAt: Long) {
        cacheDao.upsert(
            ExchangeRateCache(
                pairKey = ExchangeRateCache.pairKey(instrument.code, intermediary.code),
                instrumentCode = instrument.code,
                intermediaryCode = intermediary.code,
                rate = rate,
                fetchedAt = fetchedAt
            )
        )
    }

    companion object {
        private const val TAG = "ExchangeRateRepository"
        private const val EXCHANGE_ID = "binance"
    }
}
