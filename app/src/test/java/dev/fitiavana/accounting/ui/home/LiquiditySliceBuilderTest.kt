package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.LiquidityLevels
import dev.fitiavana.accounting.features.balances.AccountBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquiditySliceBuilderTest {

    private fun account(id: String, type: String = "asset", liquidityLevel: String? = null) =
        Account(id = id, name = id, type = type, liquidityLevel = liquidityLevel)

    private fun balance(accountId: String, amount: Long) =
        AccountBalance(accountId = accountId, balance = amount, updatedAt = 0L, createdAt = 0L)

    @Test
    fun `groups asset balances by liquidity level in LiquidityLevels order, unclassified last`() {
        val accounts = listOf(
            account("a", liquidityLevel = LiquidityLevels.STOCKS),
            account("b", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
            account("c", liquidityLevel = null)
        )
        val balances = listOf(balance("a", 500_000), balance("b", 200_000), balance("c", 50_000))

        val result = LiquiditySliceBuilder.liquiditySlices(accounts, balances)

        assertEquals(
            listOf(
                AssetSlice("Cash & Cash Equivalents", 200_000),
                AssetSlice("Stocks", 500_000),
                AssetSlice("Unclassified", 50_000)
            ),
            result
        )
    }

    @Test
    fun `sums multiple accounts sharing the same liquidity level into one slice`() {
        val accounts = listOf(
            account("a", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
            account("b", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS)
        )
        val balances = listOf(balance("a", 100_000), balance("b", 50_000))

        val result = LiquiditySliceBuilder.liquiditySlices(accounts, balances)

        assertEquals(listOf(AssetSlice("Cash & Cash Equivalents", 150_000)), result)
    }

    @Test
    fun `liquidity levels with no accounts produce no slice`() {
        val accounts = listOf(account("a", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS))
        val balances = listOf(balance("a", 100_000))

        val result = LiquiditySliceBuilder.liquiditySlices(accounts, balances)

        assertEquals(1, result.size)
    }

    @Test
    fun `ignores non-asset accounts even if they carry a liquidity level`() {
        val accounts = listOf(
            account("a", type = "liability", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS)
        )
        val balances = listOf(balance("a", 100_000))

        assertTrue(LiquiditySliceBuilder.liquiditySlices(accounts, balances).isEmpty())
    }

    @Test
    fun `returns empty list when there are no asset balances`() {
        assertTrue(LiquiditySliceBuilder.liquiditySlices(emptyList(), emptyList()).isEmpty())
    }
}
