package dev.fitiavana.accounting.features.cexprices

import dev.fitiavana.accounting.network.cex.CexId
import dev.fitiavana.accounting.network.cex.CexPriceFetcher
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Outcome for one exchange: either a [price] or the [error] explaining why there is none. */
data class CexPrice(val cex: CexId, val price: Double?, val error: Exception?)

class CexPriceRepository(
    private val fetchers: Map<CexId, CexPriceFetcher>,
    private val pairStore: CexPairStore
) {

    fun getLastPair(): Pair<String, String>? = pairStore.load()

    fun saveLastPair(base: String, quote: String) = pairStore.save(base, quote)

    /**
     * Queries every exchange concurrently and returns one result per exchange, in [CexId] order.
     * A failing exchange never affects the others. Blocks until all are done — call off the main thread.
     */
    fun fetchAll(base: String, quote: String): List<CexPrice> {
        val entries = fetchers.entries.sortedBy { it.key.ordinal }
        val executor = Executors.newFixedThreadPool(entries.size.coerceAtLeast(1))
        try {
            val tasks = entries.map { (cex, fetcher) ->
                Callable {
                    try {
                        CexPrice(cex, fetcher.fetchPrice(base, quote), null)
                    } catch (e: Exception) {
                        CexPrice(cex, null, e)
                    }
                }
            }
            return executor.invokeAll(tasks).map { it.get() }
        } finally {
            executor.shutdown()
        }
    }
}
