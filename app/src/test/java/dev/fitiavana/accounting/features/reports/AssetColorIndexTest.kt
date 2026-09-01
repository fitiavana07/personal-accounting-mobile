package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetColorIndexTest {

    private fun account(id: String, name: String, type: String) =
        Account(id = id, name = name, type = type)

    private fun balance(accountId: String, balance: Long) =
        AccountBalance(accountId = accountId, balance = balance, updatedAt = 0L, createdAt = 0L)

    @Test
    fun `indexes qualifying asset accounts by balance descending`() {
        val result = AssetColorIndex.compute(
            accounts = listOf(
                account("a", "Bank", "asset"),
                account("b", "Cash", "asset")
            ),
            balances = listOf(balance("a", 15_000), balance("b", 20_000))
        )

        assertEquals(
            mapOf("b" to 0, "a" to 1),
            result.colorIndexByAccountId()
        )
    }

    @Test
    fun `accounts below the Other threshold share one trailing index`() {
        val result = AssetColorIndex.compute(
            accounts = listOf(
                account("a", "Bank", "asset"),
                account("b", "Petty Cash", "asset"),
                account("c", "Coin Jar", "asset")
            ),
            balances = listOf(balance("a", 15_000), balance("b", 4_000), balance("c", 2_500))
        )

        assertEquals(
            mapOf("a" to 0, "b" to 1, "c" to 1),
            result.colorIndexByAccountId()
        )
    }

    @Test
    fun `excludes non-asset accounts and zero balances`() {
        val result = AssetColorIndex.compute(
            accounts = listOf(
                account("a", "Cash", "asset"),
                account("b", "Empty Wallet", "asset"),
                account("c", "Loan", "liability")
            ),
            balances = listOf(balance("a", 10_000), balance("b", 0), balance("c", 5_000))
        )

        assertEquals(mapOf("a" to 0), result.colorIndexByAccountId())
    }
}
