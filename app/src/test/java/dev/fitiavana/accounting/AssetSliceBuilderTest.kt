package dev.fitiavana.accounting

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.ui.home.AssetSlice
import dev.fitiavana.accounting.ui.home.AssetSliceBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetSliceBuilderTest {

    private fun account(id: String, name: String, type: String) =
        Account(id = id, name = name, type = type)

    private fun balance(accountId: String, balance: Long) =
        AccountBalance(accountId = accountId, balance = balance, updatedAt = 0L, createdAt = 0L)

    @Test
    fun `returns one slice per asset account when all are above the threshold`() {
        val result = AssetSliceBuilder.assetSlices(
            accounts = listOf(
                account("a", "Bank", "asset"),
                account("b", "Cash", "asset")
            ),
            balances = listOf(balance("a", 15_000), balance("b", 20_000))
        )

        assertEquals(
            listOf(AssetSlice("Cash", 20_000), AssetSlice("Bank", 15_000)),
            result
        )
    }

    @Test
    fun `groups accounts under 10000Ar into a single Other slice`() {
        val result = AssetSliceBuilder.assetSlices(
            accounts = listOf(
                account("a", "Bank", "asset"),
                account("b", "Petty Cash", "asset"),
                account("c", "Coin Jar", "asset")
            ),
            balances = listOf(balance("a", 15_000), balance("b", 4_000), balance("c", 2_500))
        )

        assertEquals(
            listOf(AssetSlice("Bank", 15_000), AssetSlice("Other", 6_500)),
            result
        )
    }

    @Test
    fun `excludes non-asset accounts and zero balances`() {
        val result = AssetSliceBuilder.assetSlices(
            accounts = listOf(
                account("a", "Cash", "asset"),
                account("b", "Empty Wallet", "asset"),
                account("c", "Loan", "liability")
            ),
            balances = listOf(balance("a", 10_000), balance("b", 0), balance("c", 5_000))
        )

        assertEquals(listOf(AssetSlice("Cash", 10_000)), result)
    }

    @Test
    fun `returns empty list when there are no asset accounts`() {
        val result = AssetSliceBuilder.assetSlices(
            accounts = listOf(account("a", "Loan", "liability")),
            balances = listOf(balance("a", 5_000))
        )

        assertTrue(result.isEmpty())
    }
}
