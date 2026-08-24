package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.reports.BalanceSheetBuilder
import dev.fitiavana.accounting.features.reports.BalanceSheetRow
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
                BalanceSheetRow.AccountLine("Cash", 10_000, assetIndex = 0),
                BalanceSheetRow.TotalLine("Total Assets", 10_000, emphasized = true),
                BalanceSheetRow.DateLine(0L)
            ),
            result
        )
    }

    @Test
    fun `accounts within a non-asset type are sorted by name`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("acc1", "Zebra Loan", "liability"),
                account("acc2", "Alpha Loan", "liability")
            ),
            balances = listOf(balance("acc1", 10_000), balance("acc2", 20_000))
        )

        val accountLines = result.filterIsInstance<BalanceSheetRow.AccountLine>()
        assertEquals(listOf("Alpha Loan", "Zebra Loan"), accountLines.map { it.name })
    }

    @Test
    fun `asset accounts are sorted by balance decreasing`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("acc1", "Zebra Bank", "asset"),
                account("acc2", "Alpha Bank", "asset")
            ),
            balances = listOf(balance("acc1", 10_000), balance("acc2", 20_000))
        )

        val accountLines = result.filterIsInstance<BalanceSheetRow.AccountLine>()
        assertEquals(listOf("Alpha Bank", "Zebra Bank"), accountLines.map { it.name })
    }

    @Test
    fun `income accounts are sorted by balance decreasing`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("acc1", "Small Client", "revenue"),
                account("acc2", "Big Client", "revenue")
            ),
            balances = listOf(balance("acc1", 100), balance("acc2", 500))
        )

        val accountLines = result.filterIsInstance<BalanceSheetRow.AccountLine>()
        assertEquals(listOf("Big Client", "Small Client"), accountLines.map { it.name })
    }

    @Test
    fun `expense accounts are sorted by balance decreasing`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("acc1", "Coffee", "expense"),
                account("acc2", "Rent", "expense")
            ),
            balances = listOf(balance("acc1", 50), balance("acc2", 800))
        )

        val accountLines = result.filterIsInstance<BalanceSheetRow.AccountLine>()
        assertEquals(listOf("Rent", "Coffee"), accountLines.map { it.name })
    }

    @Test
    fun `all eight account types render in the expected order with correct raw amounts`() {
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
            balance("a", 10_000, updatedAt = 100),
            balance("l", 200, updatedAt = 200),
            balance("e", 500, updatedAt = 50),
            balance("r", 300, updatedAt = 300),
            balance("x", 150, updatedAt = 10),
            balance("g", 80, updatedAt = 20),
            balance("o", 30, updatedAt = 30),
            balance("d", 60, updatedAt = 40)
        )

        val result = BalanceSheetBuilder.build(accounts, balances)

        // Total Changes in Equity = 300 - 150 + 80 - 30 - 60 = 140
        // Total Equity = 500 + 140 = 640
        assertEquals(
            listOf(
                BalanceSheetRow.Title("Instant Balance Sheet"),
                BalanceSheetRow.SectionHeader("Assets"),
                BalanceSheetRow.AccountLine("Cash", 10_000, assetIndex = 0),
                BalanceSheetRow.TotalLine("Total Assets", 10_000, emphasized = true),
                BalanceSheetRow.SectionHeader("Liabilities"),
                BalanceSheetRow.AccountLine("Loan", 200),
                BalanceSheetRow.TotalLine("Total Liabilities", 200, emphasized = true),
                BalanceSheetRow.SectionHeader("Equity"),
                BalanceSheetRow.SubsectionHeader("Original Equity"),
                BalanceSheetRow.AccountLine("Owner Capital", 500),
                BalanceSheetRow.TotalLine("Total Original Equity", 500),
                BalanceSheetRow.SubsectionHeader("Income"),
                BalanceSheetRow.AccountLine("Salary", 300),
                BalanceSheetRow.TotalLine("Total Income", 300),
                BalanceSheetRow.SubsectionHeader("Expense"),
                BalanceSheetRow.AccountLine("Rent", 150, contra = true),
                BalanceSheetRow.TotalLine("Total Expense", 150, contra = true),
                BalanceSheetRow.SubsectionHeader("Gain"),
                BalanceSheetRow.AccountLine("Stock Gain", 80),
                BalanceSheetRow.TotalLine("Total Gain", 80),
                BalanceSheetRow.SubsectionHeader("Loss"),
                BalanceSheetRow.AccountLine("Stock Loss", 30, contra = true),
                BalanceSheetRow.TotalLine("Total Loss", 30, contra = true),
                BalanceSheetRow.SubsectionHeader("Drawing"),
                BalanceSheetRow.AccountLine("Owner Drawing", 60, contra = true),
                BalanceSheetRow.TotalLine("Total Drawing", 60, contra = true),
                BalanceSheetRow.TotalLine("Total Changes in Equity", 140, contra = true),
                BalanceSheetRow.TotalLine("Total Equity", 640, emphasized = true),
                BalanceSheetRow.DateLine(300L)
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
                BalanceSheetRow.AccountLine("Stock Gain", 80),
                BalanceSheetRow.TotalLine("Total Gain", 80),
                BalanceSheetRow.TotalLine("Total Changes in Equity", 80, contra = true),
                BalanceSheetRow.TotalLine("Total Equity", 80, emphasized = true),
                BalanceSheetRow.DateLine(0L)
            ),
            result
        )
    }

    @Test
    fun `total equity carries a negative raw amount when equity is negative`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("x", "Rent", "expense")),
            balances = listOf(balance("x", 500))
        )

        val totalEquity = result.filterIsInstance<BalanceSheetRow.TotalLine>().last { it.label == "Total Equity" }
        assertEquals(-500L, totalEquity.amount)
        assertEquals(false, totalEquity.contra)
    }

    @Test
    fun `total changes in equity sums income, expense, gain, loss and drawing`() {
        val accounts = listOf(
            account("r", "Salary", "revenue"),
            account("x", "Rent", "expense"),
            account("d", "Owner Drawing", "drawing")
        )
        val balances = listOf(balance("r", 300), balance("x", 900), balance("d", 60))

        val result = BalanceSheetBuilder.build(accounts, balances)
        val totals = result.filterIsInstance<BalanceSheetRow.TotalLine>()

        // 300 - 900 - 60 = -660
        val changesInEquity = totals.single { it.label == "Total Changes in Equity" }
        assertEquals(-660L, changesInEquity.amount)
        assertEquals(true, changesInEquity.contra)
        assertEquals(false, changesInEquity.emphasized)

        val drawingIndex = result.indexOfFirst { it is BalanceSheetRow.TotalLine && it.label == "Total Drawing" }
        val changesIndex = result.indexOfFirst { it is BalanceSheetRow.TotalLine && it.label == "Total Changes in Equity" }
        val totalEquityIndex = result.indexOfFirst { it is BalanceSheetRow.TotalLine && it.label == "Total Equity" }
        assertTrue(drawingIndex < changesIndex)
        assertTrue(changesIndex < totalEquityIndex)
    }

    @Test
    fun `total changes in equity is omitted when there is no equity activity`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("e", "Owner Capital", "equity")),
            balances = listOf(balance("e", 800))
        )

        assertTrue(result.none { it is BalanceSheetRow.TotalLine && it.label == "Total Changes in Equity" })
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
    fun `asset accounts under 10000Ar are grouped into an Other line`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("a", "Bank", "asset"),
                account("b", "Petty Cash", "asset"),
                account("c", "Coin Jar", "asset")
            ),
            balances = listOf(balance("a", 15_000), balance("b", 4_000), balance("c", 2_500))
        )

        assertEquals(
            listOf(
                BalanceSheetRow.Title("Instant Balance Sheet"),
                BalanceSheetRow.SectionHeader("Assets"),
                BalanceSheetRow.AccountLine("Bank", 15_000, assetIndex = 0),
                BalanceSheetRow.AccountLine("Other", 6_500, assetIndex = 1),
                BalanceSheetRow.TotalLine("Total Assets", 21_500, emphasized = true),
                BalanceSheetRow.DateLine(0L)
            ),
            result
        )
    }

    @Test
    fun `asset account exactly at the 10000Ar threshold is not grouped`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("a", "Bank", "asset")),
            balances = listOf(balance("a", 10_000))
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
            balances = listOf(balance("a", 10_000), balance("z", 0))
        )

        val accountLines = result.filterIsInstance<BalanceSheetRow.AccountLine>()
        assertEquals(listOf("Cash"), accountLines.map { it.name })

        val totalAssets = result.filterIsInstance<BalanceSheetRow.TotalLine>().single { it.label == "Total Assets" }
        assertEquals(10_000L, totalAssets.amount)
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
        assertEquals(10L, dateLine.timestampMs)
        assertTrue(result.filterIsInstance<BalanceSheetRow.AccountLine>().none { it.name.isEmpty() })
        assertEquals(1, result.filterIsInstance<BalanceSheetRow.AccountLine>().size)
    }

    // --- buildMonthly ---

    @Test
    fun `buildMonthly with no balances yields empty result`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(account("acc1", "Cash", "asset")),
            balancesByAccountId = emptyMap()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildMonthly does not emit a Title or DateLine row`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(account("acc1", "Cash", "asset")),
            balancesByAccountId = mapOf("acc1" to 10_000L)
        )
        assertTrue(result.none { it is BalanceSheetRow.Title || it is BalanceSheetRow.DateLine })
    }

    @Test
    fun `buildMonthly asset lines have no asset index`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(account("acc1", "Cash", "asset")),
            balancesByAccountId = mapOf("acc1" to 10_000L)
        )
        val accountLines = result.filterIsInstance<BalanceSheetRow.AccountLine>()
        assertEquals(listOf<Int?>(null), accountLines.map { it.assetIndex })
    }

    @Test
    fun `buildMonthly collapses income, expense, gain and loss into Unclosed Income Statement accounts`() {
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
        val balances = mapOf(
            "a" to 10_000L, "l" to 200L, "e" to 500L, "r" to 300L,
            "x" to 150L, "g" to 80L, "o" to 30L, "d" to 60L
        )

        val result = BalanceSheetBuilder.buildMonthly(accounts, balances)

        // Total Unclosed IS accounts = 300 - 150 + 80 - 30 = 200
        // Total Equity = 500 + 200 - 60 = 640
        assertEquals(
            listOf(
                BalanceSheetRow.SectionHeader("Assets"),
                BalanceSheetRow.AccountLine("Cash", 10_000),
                BalanceSheetRow.TotalLine("Total Assets", 10_000, emphasized = true),
                BalanceSheetRow.SectionHeader("Liabilities"),
                BalanceSheetRow.AccountLine("Loan", 200),
                BalanceSheetRow.TotalLine("Total Liabilities", 200, emphasized = true),
                BalanceSheetRow.SectionHeader("Equity"),
                BalanceSheetRow.SubsectionHeader("Original Equity"),
                BalanceSheetRow.AccountLine("Owner Capital", 500),
                BalanceSheetRow.TotalLine("Total Original Equity", 500),
                BalanceSheetRow.SubsectionHeader("Unclosed Income Statement accounts"),
                BalanceSheetRow.AccountLine("Income", 300, arPrefixed = true),
                BalanceSheetRow.AccountLine("Expense", 150, contra = true, arPrefixed = true),
                BalanceSheetRow.AccountLine("Gain", 80, arPrefixed = true),
                BalanceSheetRow.AccountLine("Loss", 30, contra = true, arPrefixed = true),
                BalanceSheetRow.TotalLine("Total Unclosed IS accounts", 200),
                BalanceSheetRow.SubsectionHeader("Drawing"),
                BalanceSheetRow.AccountLine("Owner Drawing", 60, contra = true),
                BalanceSheetRow.TotalLine("Total Drawing", 60, contra = true),
                BalanceSheetRow.TotalLine("Total Equity", 640, emphasized = true)
            ),
            result
        )
    }

    @Test
    fun `buildMonthly never emits a Total Changes in Equity line`() {
        val accounts = listOf(
            account("r", "Salary", "revenue"),
            account("d", "Owner Drawing", "drawing")
        )
        val balances = mapOf("r" to 300L, "d" to 60L)

        val result = BalanceSheetBuilder.buildMonthly(accounts, balances)

        assertTrue(result.none { it is BalanceSheetRow.TotalLine && it.label == "Total Changes in Equity" })
    }

    @Test
    fun `buildMonthly omits Unclosed Income Statement accounts subsection when empty`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(account("e", "Owner Capital", "equity")),
            balancesByAccountId = mapOf("e" to 500L)
        )

        assertTrue(result.none { it is BalanceSheetRow.SubsectionHeader && it.title == "Unclosed Income Statement accounts" })
    }

    @Test
    fun `buildMonthly Equity section appears when only unclosed IS or drawing accounts exist`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(account("d", "Owner Drawing", "drawing")),
            balancesByAccountId = mapOf("d" to 60L)
        )

        assertTrue(result.any { it is BalanceSheetRow.SectionHeader && it.title == "Equity" })
        val totalEquity = result.filterIsInstance<BalanceSheetRow.TotalLine>().single { it.label == "Total Equity" }
        assertEquals(-60L, totalEquity.amount)
    }

    @Test
    fun `buildMonthly asset accounts under 10000Ar are grouped into an Other line with no asset index`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(
                account("a", "Bank", "asset"),
                account("b", "Petty Cash", "asset"),
                account("c", "Coin Jar", "asset")
            ),
            balancesByAccountId = mapOf("a" to 15_000L, "b" to 4_000L, "c" to 2_500L)
        )

        assertEquals(
            listOf(
                BalanceSheetRow.SectionHeader("Assets"),
                BalanceSheetRow.AccountLine("Bank", 15_000),
                BalanceSheetRow.AccountLine("Other", 6_500),
                BalanceSheetRow.TotalLine("Total Assets", 21_500, emphasized = true)
            ),
            result
        )
    }
}
