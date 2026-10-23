package dev.fitiavana.accounting.features.balances

/** The signed one-decimal percent shown for gains and losses, e.g. "+12.3%" or "-4.0%". */
object SignedPercent {
    fun format(value: Double): String {
        val sign = if (value >= 0) "+" else "-"
        return String.format("%s%.1f%%", sign, Math.abs(value))
    }
}
