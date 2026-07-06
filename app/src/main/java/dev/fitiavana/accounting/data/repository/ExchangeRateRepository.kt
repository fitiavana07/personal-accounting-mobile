package dev.fitiavana.accounting.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.data.dao.ExchangeRateCacheDao
import dev.fitiavana.accounting.data.model.ExchangeRateCache
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.network.CoinGeckoClient
import dev.fitiavana.accounting.data.network.ExchangeRateFetcher
import java.io.IOException

data class RefreshResult(
    val succeeded: List<String>,
    val failed: List<String>,
    val error: String?
)

class ExchangeRateRepository(
    private val cacheDao: ExchangeRateCacheDao,
    private val fetcher: ExchangeRateFetcher = CoinGeckoClient()
) {
    fun getAllCached(): LiveData<List<ExchangeRateCache>> = cacheDao.getAll()

    /**
     * Fetches current rates for each (instrument, intermediaryInstrument) pair and
     * upserts them into the cache. Runs synchronously — callers must invoke this off
     * the main thread. Pairs whose instrument has no coingeckoId are reported as failed
     * without hitting the network.
     *
     * Rates come from CoinGecko's `/coins/{id}/tickers` endpoint, which returns the
     * actual exchange-quoted price for a coin against every currency it's traded
     * against on that exchange (e.g. Binance's live BTC/USDT price) — this is the real
     * traded pair, not a synthetic rate derived via an intermediate reference currency.
     * One request is made per distinct instrument (its tickers cover every intermediary
     * that instrument is paired with).
     */
    fun refresh(pairs: List<Pair<Instrument, Instrument>>): RefreshResult {
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
