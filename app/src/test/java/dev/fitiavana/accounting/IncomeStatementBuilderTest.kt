package dev.fitiavana.accounting

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.reports.BalanceSheetRow
import dev.fitiavana.accounting.features.reports.IncomeStatementBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeStatementBuilderTest {

    private fun account(id: String, name: String, type: String) =
        Account(id = id, name = name, type = type)

    @Test
    fun `no balances yields empty result`() {
        val result = IncomeStatementBuilder.build(
            accounts = listOf(account("a", "Cash", "asset")),
            balancesByAccountId = emptyMap()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `renders income, expense, gain and loss as main sections with Net Income last`() {
        val accounts = listOf(
            account("a", "Cash", "asset"),
            account("r", "Salary", "revenue"),
            account("x", "Rent", "expense"),
            account("g", "Stock Gain", "gain"),
            account("o", "Stock Loss", "loss")
        )
        val balances = mapOf("a" to 10_000L, "r" to 300L, "x" to 150L, "g" to 80L, "o" to 30L)

        val result = IncomeStatementBuilder.build(accounts, balances)

        // Net Income = 300 - 150 + 80 - 30 = 200
        assertEquals(
            listOf(
                BalanceSheetRow.SectionHeader("Income"),
                BalanceSheetRow.AccountLine("Salary", 300),
                BalanceSheetRow.TotalLine("Total Income", 300, emphasized = true),
                BalanceSheetRow.SectionHeader("Expense"),
                BalanceSheetRow.AccountLine("Rent", 150, contra = true),
                BalanceSheetRow.TotalLine("Total Expense", 150, emphasized = true, contra = true),
                BalanceSheetRow.SectionHeader("Gain"),
                BalanceSheetRow.AccountLine("Stock Gain", 80),
                BalanceSheetRow.TotalLine("Total Gain", 80, emphasized = true),
                BalanceSheetRow.SectionHeader("Loss"),
                BalanceSheetRow.AccountLine("Stock Loss", 30, contra = true),
                BalanceSheetRow.TotalLine("Total Loss", 30, emphasized = true, contra = true),
                BalanceSheetRow.TotalLine("Net Income", 200, emphasized = true)
            ),
            result
        )
        assertTrue(result.none { it is BalanceSheetRow.SectionHeader && it.title in listOf("Assets", "Liabilities", "Equity") })
    }

    @Test
    fun `omits sections with no accounts`() {
        val result = IncomeStatementBuilder.build(
            accounts = listOf(account("r", "Salary", "revenue")),
            balancesByAccountId = mapOf("r" to 300L)
        )

        assertTrue(result.none { it is BalanceSheetRow.SectionHeader && it.title in listOf("Expense", "Gain", "Loss") })
        val netIncome = result.filterIsInstance<BalanceSheetRow.TotalLine>().single { it.label == "Net Income" }
        assertEquals(300L, netIncome.amount)
    }

    @Test
    fun `Net Income carries a negative raw amount when negative, without the contra flag`() {
        val result = IncomeStatementBuilder.build(
            accounts = listOf(account("x", "Rent", "expense")),
            balancesByAccountId = mapOf("x" to 500L)
        )

        val netIncome = result.filterIsInstance<BalanceSheetRow.TotalLine>().single { it.label == "Net Income" }
        assertEquals(-500L, netIncome.amount)
        assertEquals(false, netIncome.contra)
    }

    @Test
    fun `sorts income and expense accounts by balance decreasing`() {
        val result = IncomeStatementBuilder.build(
            accounts = listOf(
                account("r1", "Small Client", "revenue"),
                account("r2", "Big Client", "revenue")
            ),
            balancesByAccountId = mapOf("r1" to 100L, "r2" to 500L)
        )

        val accountLines = result.filterIsInstance<BalanceSheetRow.AccountLine>()
        assertEquals(listOf("Big Client", "Small Client"), accountLines.map { it.name })
    }
}
