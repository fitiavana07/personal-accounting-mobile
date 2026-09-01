package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.LiquidityLevels
import dev.fitiavana.accounting.features.balances.AccountBalance
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidAssetsBuilderTest {

    private fun account(id: String, type: String = "asset", liquidityLevel: String? = null) =
        Account(id = id, name = id, type = type, liquidityLevel = liquidityLevel)

    private fun balance(accountId: String, amount: Long) = AccountBalance(
        accountId = accountId,
        balance = amount,
        instrumentBalance = 0,
        intermediaryBalance = 0,
        updatedAt = 0L,
        createdAt = 0L
    )

    @Test
    fun `sums only asset balances with cash-and-equivalents liquidity level`() {
        val accounts = listOf(
            account("acc1", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
            account("acc2", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
            account("acc3", liquidityLevel = LiquidityLevels.STOCKS),
            account("acc4", liquidityLevel = null)
        )
        val balances = listOf(
            balance("acc1", 100_000),
            balance("acc2", 50_000),
            balance("acc3", 1_000_000),
            balance("acc4", 200_000)
        )

        assertEquals(150_000L, LiquidAssetsBuilder.totalLiquidAssets(accounts, balances))
    }

    @Test
    fun `ignores balances for accounts not of asset type even with cash liquidity level`() {
        val accounts = listOf(
            account("acc1", type = "liability", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS)
        )
        val balances = listOf(balance("acc1", 100_000))

        assertEquals(0L, LiquidAssetsBuilder.totalLiquidAssets(accounts, balances))
    }

    @Test
    fun `returns zero when there are no matching balances`() {
        assertEquals(0L, LiquidAssetsBuilder.totalLiquidAssets(emptyList(), emptyList()))
    }
}
