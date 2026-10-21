package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.balances.GainLossCalculator

/** Portfolio-wide gain/loss in the base currency, over the items that have a rate. */
data class HomeGainLossSummary(
    val gainLossAr: Double,
    val gainLossPercent: Double?,
    val bookValueAr: Long
) {
    companion object {
        fun of(items: List<HomeItem>): HomeGainLossSummary? {
            val priced = items.filter { it.gainLossAr != null }
            if (priced.isEmpty()) return null
            val gainLossAr = priced.sumOf { it.gainLossAr!! }
            val bookValueAr = priced.sumOf { it.bookValueAr }
            return HomeGainLossSummary(
                gainLossAr = gainLossAr,
                gainLossPercent = GainLossCalculator.computeGainLossPercent(gainLossAr, bookValueAr.toDouble()),
                bookValueAr = bookValueAr
            )
        }
    }
}
