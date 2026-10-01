package dev.fitiavana.accounting.features.p2pprices

import dev.fitiavana.accounting.network.p2p.P2pAd
import dev.fitiavana.accounting.network.p2p.P2pPaymentMethod
import dev.fitiavana.accounting.network.p2p.P2pPriceFetcher
import dev.fitiavana.accounting.network.p2p.P2pSide
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Top ad prices per side; a null list means that side could not be fetched.
 * [filter] is the payment method the ads were filtered on, null when unfiltered.
 */
data class P2pPrices(val buy: List<P2pAd>?, val sell: List<P2pAd>?, val filter: P2pPaymentMethod? = null)

class P2pPriceRepository(
    private val fetcher: P2pPriceFetcher,
    private val store: P2pFilterStore
) {

    fun getSelectedMethod(): String? = store.loadSelected()

    /** Persists the payment method to filter on; null clears the filter. */
    fun selectMethod(identifier: String?) = store.saveSelected(identifier)

    /** The cached payment methods, fetched (and cached) first when there are none. Empty if that fails. */
    fun getPaymentMethods(): List<P2pPaymentMethod> =
        store.loadMethods() ?: refreshMethods() ?: emptyList()

    /**
     * Fetches both sides concurrently, filtered on the selected payment method; one failing never
     * affects the other. If a filter yields no ads at all, its identifier may have been retired:
     * the method list is refreshed and, when the selection is gone, cleared and prices refetched unfiltered.
     * Blocks until done — call off the main thread.
     */
    fun fetch(): P2pPrices {
        val selected = store.loadSelected()
        var prices = fetchSides(selected)
        if (selected != null && prices.buy?.isEmpty() == true && prices.sell?.isEmpty() == true) {
            val methods = refreshMethods()
            if (methods != null && methods.none { it.identifier == selected }) {
                store.saveSelected(null)
                prices = fetchSides(null)
            }
        }
        return prices.copy(filter = store.loadSelected()?.let(::methodFor))
    }

    /** The cached method for [identifier], or one named after its identifier when it isn't cached. */
    private fun methodFor(identifier: String) =
        store.loadMethods()?.firstOrNull { it.identifier == identifier } ?: P2pPaymentMethod(identifier, identifier)

    private fun fetchSides(payType: String?): P2pPrices {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(
                P2pSide.values().map { side ->
                    Callable { try { fetcher.fetchTopPrices(side, payType) } catch (e: Exception) { null } }
                }
            ).map { it.get() }
            return P2pPrices(buy = results[P2pSide.BUY.ordinal], sell = results[P2pSide.SELL.ordinal])
        } finally {
            executor.shutdown()
        }
    }

    /** Re-fetches and caches the method list; null if the request fails or Binance returns none. */
    private fun refreshMethods(): List<P2pPaymentMethod>? =
        try {
            fetcher.fetchPaymentMethods().takeIf { it.isNotEmpty() }?.also { store.saveMethods(it) }
        } catch (e: Exception) {
            null
        }
}
