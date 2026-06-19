package dev.fitiavana.accounting

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.ui.balances.BalanceItem
import dev.fitiavana.accounting.ui.balances.BalanceItemBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceItemBuilderTest {

    private fun account(id: String, name: String) =
        Account(id = id, name = name, type = "asset")

    private fun balance(accountId: String, balance: Int, updatedAt: Long = 1000L) =
        AccountBalance(accountId = accountId, balance = balance, updatedAt = updatedAt, createdAt = 0L)

    // --- Basic mapping ---

    @Test
    fun `empty inputs produce empty list`() {
        assertTrue(BalanceItemBuilder.build(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `balance with no matching account is excluded`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 500)),
            accounts = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `balance maps to item with correct fields`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 1000, updatedAt = 9999L)),
            accounts = listOf(account("acc1", "Cash"))
        )
        assertEquals(1, result.size)
        assertEquals(BalanceItem("acc1", "Cash", 1000, 9999L), result[0])
    }

    // --- Zero balance is included (account has transactions) ---

    @Test
    fun `zero balance account is included`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 0)),
            accounts = listOf(account("acc1", "Wash"))
        )
        assertEquals(1, result.size)
        assertEquals(0, result[0].balance)
    }

    // --- Sorting: highest balance first ---

    @Test
    fun `items sorted highest balance first`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(
                balance("acc1", 1300),
                balance("acc2", 10000),
                balance("acc3", 3000)
            ),
            accounts = listOf(
                account("acc1", "Cash"),
                account("acc2", "Capital"),
                account("acc3", "Sales")
            )
        )
        assertEquals(listOf(10000, 3000, 1300), result.map { it.balance })
    }

    @Test
    fun `negative balance sorts after positive balances`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(
                balance("acc1", -500),
                balance("acc2", 1000),
                balance("acc3", 0)
            ),
            accounts = listOf(
                account("acc1", "Overdraft"),
                account("acc2", "Cash"),
                account("acc3", "Wash")
            )
        )
        assertEquals(listOf(1000, 0, -500), result.map { it.balance })
    }

    // --- Multiple accounts, some without balances ---

    @Test
    fun `accounts without balance rows are excluded`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 500)),
            accounts = listOf(
                account("acc1", "Cash"),
                account("acc2", "NoTxAccount")
            )
        )
        assertEquals(1, result.size)
        assertEquals("acc1", result[0].accountId)
    }
}
