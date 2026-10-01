package dev.fitiavana.accounting.ui.cexprices

import dev.fitiavana.accounting.features.cexprices.CexPrice
import dev.fitiavana.accounting.network.cex.CexId

/** One exchange line: [diffPercent] is relative to the lowest price; marks only apply when prices differ. */
data class CexPriceRow(
    val cex: CexId,
    val price: Double?,
    val diffPercent: Double?,
    val isLowest: Boolean,
    val isHighest: Boolean
)

data class CexPriceComparison(val rows: List<CexPriceRow>, val spreadPercent: Double?)

object CexPriceComparisonBuilder {

    fun build(prices: List<CexPrice>): CexPriceComparison {
        val available = prices.mapNotNull { it.price }
        val lowest = available.minOrNull()
        val highest = available.maxOrNull()
        val comparable = lowest != null && highest != null && available.size > 1 && lowest > 0
        val marked = comparable && lowest != highest

        val rows = prices.map { item ->
            val price = item.price
            CexPriceRow(
                cex = item.cex,
                price = price,
                diffPercent = if (comparable && price != null) (price - lowest!!) / lowest * 100 else null,
                isLowest = marked && price == lowest,
                isHighest = marked && price == highest
            )
        }
        val spread = if (comparable) (highest!! - lowest!!) / lowest * 100 else null
        return CexPriceComparison(rows, spread)
    }
}
