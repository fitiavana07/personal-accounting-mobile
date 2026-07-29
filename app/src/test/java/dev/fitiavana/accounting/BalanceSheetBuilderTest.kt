package dev.fitiavana.accounting

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.ui.home.BalanceSheetBuilder
import dev.fitiavana.accounting.ui.home.BalanceSheetRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceSheetBuilderTest {

    private fun account(id: String, name: String, type: String) =
        Account(id = id, name = name, type = type)

    private fun balance(accountId: String, balance: Long, updatedAt: Long = 0L) =
        AccountBalance(accountId = accountId, balance = balance, updatedAt = updatedAt, createdAt = updatedAt)

    @Test
    fun `no balances yields empty result`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("acc1", "Cash", "asset")),
            balances = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single asset account produces title, assets section and no other sections`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("acc1", "Cash", "asset")),
            balances = listOf(balance("acc1", 10_000))
        )

        assertEquals(
            listOf(
                BalanceSheetRow.Title("Instant Balance Sheet"),
                BalanceSheetRow.SectionHeader("Assets"),
                BalanceSheetRow.AccountLine("Cash", "Ar 10,000"),
                BalanceSheetRow.TotalLine("Total Assets", "Ar 10,000", emphasized = true),
                BalanceSheetRow.DateLine("Balances at Jan 1, 1970")
            ),
            result
        )
    }

    @Test
    fun `accounts within a type are sorted by name`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("acc1", "Zebra Bank", "asset"),
                account("acc2", "Alpha Bank", "asset")
            ),
            balances = listOf(balance("acc1", 1_000), balance("acc2", 2_000))
        )

        val accountLines = result.filterIsInstance<BalanceSheetRow.AccountLine>()
        assertEquals(listOf("Alpha Bank", "Zebra Bank"), accountLines.map { it.name })
    }

    @Test
    fun `all eight account types render in the expected order with correct formatting`() {
        val accounts = listOf(
            account("a", "Cash", "asset"),
            account("l", "Loan", "liability"),
            account("e", "Owner Capital", "equity"),
            account("r", "Salary", "revenue"),
            account("x", "Rent", "expense"),
            account("g", "Stock Gain", "gain"),
            account("o", "Stock Loss", "loss"),
            account("d", "Owner Drawing", "drawing")
        )
        val balances = listOf(
            balance("a", 1000, updatedAt = 100),
            balance("l", 200, updatedAt = 200),
            balance("e", 500, updatedAt = 50),
            balance("r", 300, updatedAt = 300),
            balance("x", 150, updatedAt = 10),
            balance("g", 80, updatedAt = 20),
            balance("o", 30, updatedAt = 30),
            balance("d", 60, updatedAt = 40)
        )

        val result = BalanceSheetBuilder.build(accounts, balances)

        // Total Equity = 500 + 300 - 150 + 80 - 30 - 60 = 640
        assertEquals(
            listOf(
                BalanceSheetRow.Title("Instant Balance Sheet"),
                BalanceSheetRow.SectionHeader("Assets"),
                BalanceSheetRow.AccountLine("Cash", "Ar 1,000"),
                BalanceSheetRow.TotalLine("Total Assets", "Ar 1,000", emphasized = true),
                BalanceSheetRow.SectionHeader("Liabilities"),
                BalanceSheetRow.AccountLine("Loan", "Ar 200"),
                BalanceSheetRow.TotalLine("Total Liabilities", "Ar 200", emphasized = true),
                BalanceSheetRow.SectionHeader("Equity"),
                BalanceSheetRow.SubsectionHeader("Original Equity"),
                BalanceSheetRow.AccountLine("Owner Capital", "Ar 500"),
                BalanceSheetRow.TotalLine("Total Original Equity", "Ar 500"),
                BalanceSheetRow.SubsectionHeader("Income"),
                BalanceSheetRow.AccountLine("Salary", "Ar 300"),
                BalanceSheetRow.TotalLine("Total Income", "Ar 300"),
                BalanceSheetRow.SubsectionHeader("Expense"),
                BalanceSheetRow.AccountLine("Rent", "(Ar 150)"),
                BalanceSheetRow.TotalLine("Total Expense", "(Ar 150)"),
                BalanceSheetRow.SubsectionHeader("Gain"),
                BalanceSheetRow.AccountLine("Stock Gain", "Ar 80"),
                BalanceSheetRow.TotalLine("Total Gain", "Ar 80"),
                BalanceSheetRow.SubsectionHeader("Loss"),
                BalanceSheetRow.AccountLine("Stock Loss", "(Ar 30)"),
                BalanceSheetRow.TotalLine("Total Loss", "(Ar 30)"),
                BalanceSheetRow.SubsectionHeader("Drawing"),
                BalanceSheetRow.AccountLine("Owner Drawing", "(Ar 60)"),
                BalanceSheetRow.TotalLine("Total Drawing", "(Ar 60)"),
                BalanceSheetRow.TotalLine("Total Equity", "Ar 640", emphasized = true),
                BalanceSheetRow.DateLine("Balances at Jan 1, 1970")
            ),
            result
        )
    }

    @Test
    fun `equity section renders with only a populated subsection when others are empty`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("g", "Stock Gain", "gain")),
            balances = listOf(balance("g", 80))
        )

        assertEquals(
            listOf(
                BalanceSheetRow.Title("Instant Balance Sheet"),
                BalanceSheetRow.SectionHeader("Equity"),
                BalanceSheetRow.SubsectionHeader("Gain"),
                BalanceSheetRow.AccountLine("Stock Gain", "Ar 80"),
                BalanceSheetRow.TotalLine("Total Gain", "Ar 80"),
                BalanceSheetRow.TotalLine("Total Equity", "Ar 80", emphasized = true),
                BalanceSheetRow.DateLine("Balances at Jan 1, 1970")
            ),
            result
        )
    }

    @Test
    fun `total equity renders with a leading minus sign when negative`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("x", "Rent", "expense")),
            balances = listOf(balance("x", 500))
        )

        val totalEquity = result.filterIsInstance<BalanceSheetRow.TotalLine>().last { it.label == "Total Equity" }
        assertEquals("-Ar 500", totalEquity.amountText)
    }

    @Test
    fun `only the three top-level totals are emphasized`() {
        val accounts = listOf(
            account("a", "Cash", "asset"),
            account("l", "Loan", "liability"),
            account("e", "Owner Capital", "equity")
        )
        val balances = listOf(
            balance("a", 1000),
            balance("l", 200),
            balance("e", 800)
        )

        val totals = BalanceSheetBuilder.build(accounts, balances)
            .filterIsInstance<BalanceSheetRow.TotalLine>()

        assertEquals(
            listOf("Total Assets", "Total Liabilities", "Total Equity"),
            totals.filter { it.emphasized }.map { it.label }
        )
        assertEquals(
            listOf("Total Original Equity"),
            totals.filter { !it.emphasized }.map { it.label }
        )
    }

    @Test
    fun `asset accounts under 1000Ar are grouped into an Other line`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("a", "Bank", "asset"),
                account("b", "Petty Cash", "asset"),
                account("c", "Coin Jar", "asset")
            ),
            balances = listOf(balance("a", 5_000), balance("b", 400), balance("c", 250))
        )

        assertEquals(
            listOf(
                BalanceSheetRow.Title("Instant Balance Sheet"),
                BalanceSheetRow.SectionHeader("Assets"),
                BalanceSheetRow.AccountLine("Bank", "Ar 5,000"),
                BalanceSheetRow.AccountLine("Other", "Ar 650"),
                BalanceSheetRow.TotalLine("Total Assets", "Ar 5,650", emphasized = true),
                BalanceSheetRow.DateLine("Balances at Jan 1, 1970")
            ),
            result
        )
    }

    @Test
    fun `asset account exactly at the 1000Ar threshold is not grouped`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("a", "Bank", "asset")),
            balances = listOf(balance("a", 1_000))
        )

        val accountLines = result.filterIsInstance<BalanceSheetRow.AccountLine>()
        assertEquals(listOf("Bank"), accountLines.map { it.name })
    }

    @Test
    fun `zero balance accounts are hidden but still counted in totals`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("a", "Cash", "asset"),
                account("z", "Empty Wallet", "asset")
            ),
            balances = listOf(balance("a", 1000), balance("z", 0))
        )

        val accountLines = result.filterIsInstance<BalanceSheetRow.AccountLine>()
        assertEquals(listOf("Cash"), accountLines.map { it.name })

        val totalAssets = result.filterIsInstance<BalanceSheetRow.TotalLine>().single { it.label == "Total Assets" }
        assertEquals("Ar 1,000", totalAssets.amountText)
    }

    @Test
    fun `section is omitted entirely when all its accounts have zero balance`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("l", "Zero Loan", "liability")),
            balances = listOf(balance("l", 0))
        )

        assertTrue(result.none { it is BalanceSheetRow.SectionHeader && it.title == "Liabilities" })
    }

    @Test
    fun `balance row with no matching account is excluded`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("a", "Cash", "asset")),
            balances = listOf(
                balance("a", 100, updatedAt = 10),
                balance("orphan", 999, updatedAt = 99999)
            )
        )

        val dateLine = result.filterIsInstance<BalanceSheetRow.DateLine>().single()
        assertEquals("Balances at Jan 1, 1970", dateLine.text)
        assertTrue(result.filterIsInstance<BalanceSheetRow.AccountLine>().none { it.name.isEmpty() })
        assertEquals(1, result.filterIsInstance<BalanceSheetRow.AccountLine>().size)
    }
}
