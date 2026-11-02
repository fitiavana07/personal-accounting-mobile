package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
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
}
