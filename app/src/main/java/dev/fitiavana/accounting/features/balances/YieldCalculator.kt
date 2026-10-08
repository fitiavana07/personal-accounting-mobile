package dev.fitiavana.accounting.features.balances

/** The span a projected interest amount covers, as a fraction of a year. */
enum class YieldPeriod(val yearFraction: Double) {
    DAILY(1.0 / 365),
    MONTHLY(1.0 / 12),
    YEARLY(1.0)
}

/**
 * Projected interest for one account and period. [base] is in the base currency; [instrument] and
 * [intermediary] are in the account's instrument and intermediary instrument (their own minor units),
 * and null when the account has none.
 */
data class YieldAmounts(val base: Long, val instrument: Long?, val intermediary: Long?)

/**
 * Simple (not compounded) interest for Earn-style accounts. The account's single APR applies to each of
 * its balances separately, so no exchange rate or market value is involved.
 */
object YieldCalculator {

    /**
     * @return the interest [period] would pay on each balance, or null when there is no positive
     * [aprPercent]. An instrument or intermediary result is null when its balance is null.
     */
    fun project(
        aprPercent: Double?,
        balance: Long,
        instrumentBalance: Long?,
        intermediaryBalance: Long?,
        period: YieldPeriod
    ): YieldAmounts? {
        if (aprPercent == null || aprPercent <= 0.0) return null
        val rate = aprPercent / 100.0 * period.yearFraction
        return YieldAmounts(
            base = interest(balance, rate),
            instrument = instrumentBalance?.let { interest(it, rate) },
            intermediary = intermediaryBalance?.let { interest(it, rate) }
        )
    }

    private fun interest(balance: Long, rate: Double): Long = Math.round(balance * rate)
}
