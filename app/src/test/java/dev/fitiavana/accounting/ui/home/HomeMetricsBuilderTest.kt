package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.LiquidityLevels
import dev.fitiavana.accounting.features.balances.AccountBalance
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeMetricsBuilderTest {

    private fun account(
        id: String,
        type: String = "asset",
        liquidityLevel: String? = null
    ) = Account(id = id, name = id, type = type, liquidityLevel = liquidityLevel)

    private fun balance(accountId: String, amount: Long) =
        AccountBalance(accountId = accountId, balance = amount, updatedAt = 0L, createdAt = 0L)

    @Test
    fun `combines total equity, cash and the given emergency fund percent`() {
        val accounts = listOf(
            account("cash", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
            account("stock", liquidityLevel = LiquidityLevels.STOCKS),
            account("capital", type = "equity")
        )
        val balances = listOf(
            balance("cash", 200_000),
            balance("stock", 500_000),
            balance("capital", 700_000)
        )

        val result = HomeMetricsBuilder.build(accounts, balances, emergencyFundPercent = 40)

        assertEquals(
            HomeMetrics(
                totalEquity = 700_000,
                cash = 200_000,
                emergencyFundPercent = 40,
                cashToEquityPercent = 29
            ),
            result
        )
    }

    @Test
    fun `cash is zero when there are no cash-and-equivalents accounts`() {
        val accounts = listOf(account("stock", liquidityLevel = LiquidityLevels.STOCKS))
        val balances = listOf(balance("stock", 500_000))

        val result = HomeMetricsBuilder.build(accounts, balances, emergencyFundPercent = 0)

        assertEquals(0L, result.cash)
    }

    @Test
    fun `totalEquity is zero when there are no equity-affecting accounts`() {
        val accounts =
            listOf(account("cash", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS))
        val balances = listOf(balance("cash", 100_000))

        val result = HomeMetricsBuilder.build(accounts, balances, emergencyFundPercent = 0)

        assertEquals(0L, result.totalEquity)
    }

    @Test
    fun `passes the given emergency fund percent through unchanged`() {
        val result = HomeMetricsBuilder.build(emptyList(), emptyList(), emergencyFundPercent = 73)

        assertEquals(73, result.emergencyFundPercent)
    }

    @Test
    fun `cashToEquityPercent is cash divided by total equity, rounded to the nearest percent`() {
        val accounts = listOf(
            account("cash", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
            account("capital", type = "equity")
        )
        val balances = listOf(balance("cash", 50_000), balance("capital", 200_000))

        val result = HomeMetricsBuilder.build(accounts, balances, emergencyFundPercent = 0)

        assertEquals(25, result.cashToEquityPercent)
    }

    @Test
    fun `cashToEquityPercent can exceed 100 when cash is greater than total equity`() {
        val accounts = listOf(
            account("cash", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
            account("capital", type = "equity")
        )
        val balances = listOf(balance("cash", 300_000), balance("capital", 200_000))

        val result = HomeMetricsBuilder.build(accounts, balances, emergencyFundPercent = 0)

        assertEquals(150, result.cashToEquityPercent)
    }

    @Test
    fun `cashToEquityPercent is zero when total equity is zero or negative`() {
        val accounts = listOf(
            account("cash", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS)
        )
        val balances = listOf(balance("cash", 50_000))

        val result = HomeMetricsBuilder.build(accounts, balances, emergencyFundPercent = 0)

        assertEquals(0, result.cashToEquityPercent)
    }
}
