package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeeAccountsTest {

    @Test
    fun `selectable offers only expense accounts`() {
        val cash = Account(id = "cash", name = "Cash", type = "asset")
        val fuel = Account(id = "fuel", name = "Fees", type = "expense")
        val rent = Account(id = "rent", name = "Rent", type = "expense")
        val revenue = Account(id = "rev", name = "Revenue", type = "revenue")

        assertEquals(listOf(fuel, rent), FeeAccounts.selectable(listOf(cash, fuel, revenue, rent)))
    }

    private val fuel = Account(id = "fuel", name = "Fees", type = "expense")
    private val rent = Account(id = "rent", name = "Rent", type = "expense")
    private val cash = Account(id = "cash", name = "Cash", type = "asset")

    @Test
    fun `selectableExcluding omits the excluded expense account`() {
        assertEquals(listOf(rent), FeeAccounts.selectableExcluding(listOf(cash, fuel, rent), "fuel"))
    }

    @Test
    fun `selectableExcluding without an excluded id is the same as selectable`() {
        val accounts = listOf(cash, fuel, rent)

        assertEquals(FeeAccounts.selectable(accounts), FeeAccounts.selectableExcluding(accounts, null))
    }

    @Test
    fun `expenseEntry debits the amount to the account`() {
        val entry = FeeAccounts.expenseEntry("fuel", 500L)

        assertEquals("fuel", entry.accountId)
        assertEquals(500L, entry.debitAmount)
        assertNull(entry.creditAmount)
        assertNull(entry.instrumentDebitAmount)
        assertNull(entry.intermediaryDebitAmount)
    }
}
