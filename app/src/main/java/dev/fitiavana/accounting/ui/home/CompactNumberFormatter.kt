package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.ui.transactions.TransactionDisplay
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Formats amounts using compact number notation (also known as metric/short-scale suffixes:
 * K = thousand, M = million, B = billion, T = trillion), rounded to 3 significant digits.
 *
 * Examples: 10_000 -> "10.0K", 1_240_000 -> "1.24M", 30_100_000 -> "30.1M".
 */
object CompactNumberFormatter {

    private const val SIGNIFICANT_DIGITS = 3

    private val UNITS = listOf(
        1_000_000_000_000L to "T",
        1_000_000_000L to "B",
        1_000_000L to "M",
        1_000L to "K"
    )

    fun format(amount: Long): String {
        val sign = if (amount < 0) "-" else ""
        val absAmount = Math.abs(amount)

        var divisor = UNITS.firstOrNull { absAmount >= it.first }?.first
        var suffix = UNITS.firstOrNull { absAmount >= it.first }?.second
        if (divisor == null || suffix == null) {
            return "$sign${TransactionDisplay.formatAmount(absAmount)}"
        }

        var scaled = roundToSignificantDigits(absAmount.toDouble() / divisor)
        if (scaled >= 1000.0) {
            val nextUnitIndex = UNITS.indexOfFirst { it.first == divisor } - 1
            divisor = UNITS[nextUnitIndex].first
            suffix = UNITS[nextUnitIndex].second
            scaled = roundToSignificantDigits(absAmount.toDouble() / divisor)
        }

        val integerDigits = scaled.toLong().toString().length
        val decimalPlaces = (SIGNIFICANT_DIGITS - integerDigits).coerceAtLeast(0)
        return "$sign${String.format("%.${decimalPlaces}f", scaled)}$suffix"
    }

    private fun roundToSignificantDigits(value: Double): Double =
        BigDecimal(value).round(MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_UP)).toDouble()
}
