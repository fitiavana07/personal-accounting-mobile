package dev.fitiavana.accounting.ui.cexprices

import java.util.Locale

object CexPriceFormatter {
    /** Fewer decimals for large prices, more for tiny ones, always with thousands separators. */
    fun format(price: Double): String {
        val decimals = when {
            price >= 1000 -> 2
            price >= 1 -> 4
            else -> 8
        }
        return String.format(Locale.US, "%,.${decimals}f", price)
    }
}
