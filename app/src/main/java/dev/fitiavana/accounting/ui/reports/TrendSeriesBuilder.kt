package dev.fitiavana.accounting.ui.reports

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.reports.BalanceSheetBuilder
import dev.fitiavana.accounting.features.reports.IncomeStatementBuilder

/** One month of the trend charts: total equity as of the month and the month's net income. */
data class TrendPoint(val yearMonth: YearMonth, val netWorth: Long, val netIncome: Long)

/**
 * Builds the net worth / net income series by reusing the Balance Sheet's total equity and the
 * Income Statement's net income, so the charts always agree with the reports.
 */
object TrendSeriesBuilder {

    /** How many of the most recent months the Reports trend charts show. */
    const val MAX_MONTHS = 12

    /**
     * @param balancesAsOf cumulative per-account balances at the month's as-of instant
     * @param balancesBetween per-account balances accrued during the month
     */
    fun build(
        months: List<YearMonth>,
        accounts: List<Account>,
        balancesAsOf: (YearMonth) -> Map<String, Long>,
        balancesBetween: (YearMonth) -> Map<String, Long>
    ): List<TrendPoint> = months.map { month ->
        TrendPoint(
            yearMonth = month,
            netWorth = BalanceSheetBuilder.totalEquity(accounts, balancesAsOf(month)),
            netIncome = IncomeStatementBuilder.netIncome(accounts, balancesBetween(month))
        )
    }
}
