package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.LiquidityLevels
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.reports.BalanceSheetBuilder
import kotlin.math.roundToInt

data class HomeMetrics(
    val totalEquity: Long,
    val cash: Long,
    val emergencyFundPercent: Int,
    val cashToEquityPercent: Int
)

/** Home screen's top-level "Metrics" block: equity, cash, and emergency fund progress at a glance. */
object HomeMetricsBuilder {

    fun build(
        accounts: List<Account>,
        balances: List<AccountBalance>,
        emergencyFundPercent: Int
    ): HomeMetrics {
        val balancesByAccountId = balances.associate { it.accountId to it.balance }
        val cashLabel = LiquidityLevels.displayName(LiquidityLevels.CASH_AND_EQUIVALENTS)
        val cash = LiquiditySliceBuilder.liquiditySlices(accounts, balances)
            .firstOrNull { it.name == cashLabel }
            ?.amount ?: 0L
        val totalEquity = BalanceSheetBuilder.totalEquity(accounts, balancesByAccountId)

        return HomeMetrics(
            totalEquity = totalEquity,
            cash = cash,
            emergencyFundPercent = emergencyFundPercent,
            cashToEquityPercent = cashToEquityPercent(cash, totalEquity)
        )
    }

    private fun cashToEquityPercent(cash: Long, totalEquity: Long): Int {
        if (totalEquity <= 0) return 0
        return (cash.toDouble() / totalEquity * 100).roundToInt()
    }
}
