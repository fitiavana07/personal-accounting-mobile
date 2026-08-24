package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.reports.BalanceSheetBuilder
import dev.fitiavana.accounting.features.reports.ReportRow
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
    fun `single asset account produces title, account line, total and date`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("acc1", "Cash", "asset")),
            balances = listOf(balance("acc1", 10_000))
        )

        assertEquals(
            listOf(
                ReportRow.Title("ASSETS"),
                ReportRow.AccountLine("Cash", 10_000, assetIndex = 0),
                ReportRow.TotalLine("Total Assets", 10_000, emphasized = true),
                ReportRow.DateLine(0L)
            ),
            result
        )
    }

    @Test
    fun `non-asset accounts produce no account lines`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("acc1", "Loan", "liability"),
                account("acc2", "Owner Capital", "equity")
            ),
            balances = listOf(balance("acc1", 10_000), balance("acc2", 20_000))
        )

        assertTrue(result.filterIsInstance<ReportRow.AccountLine>().isEmpty())
        assertTrue(result.filterIsInstance<ReportRow.TotalLine>().isEmpty())
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

        val accountLines = result.filterIsInstance<ReportRow.AccountLine>()
        assertEquals(listOf("Alpha Bank", "Zebra Bank"), accountLines.map { it.name })
    }

    @Test
    fun `only Total Assets is emphasized`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("a", "Cash", "asset")),
            balances = listOf(balance("a", 1000))
        )

        val totals = result.filterIsInstance<ReportRow.TotalLine>()
        assertEquals(listOf("Total Assets"), totals.filter { it.emphasized }.map { it.label })
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
                ReportRow.Title("ASSETS"),
                ReportRow.AccountLine("Bank", 15_000, assetIndex = 0),
                ReportRow.AccountLine("Other", 6_500, assetIndex = 1),
                ReportRow.TotalLine("Total Assets", 21_500, emphasized = true),
                ReportRow.DateLine(0L)
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

        val accountLines = result.filterIsInstance<ReportRow.AccountLine>()
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

        val accountLines = result.filterIsInstance<ReportRow.AccountLine>()
        assertEquals(listOf("Cash"), accountLines.map { it.name })

        val totalAssets = result.filterIsInstance<ReportRow.TotalLine>().single { it.label == "Total Assets" }
        assertEquals(10_000L, totalAssets.amount)
    }

    @Test
    fun `no account line or total is emitted when all asset accounts have zero balance`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("a", "Empty Wallet", "asset")),
            balances = listOf(balance("a", 0))
        )

        assertTrue(result.none { it is ReportRow.AccountLine || it is ReportRow.TotalLine })
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

        val dateLine = result.filterIsInstance<ReportRow.DateLine>().single()
        assertEquals(10L, dateLine.timestampMs)
        assertTrue(result.filterIsInstance<ReportRow.AccountLine>().none { it.name.isEmpty() })
        assertEquals(1, result.filterIsInstance<ReportRow.AccountLine>().size)
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
        assertTrue(result.none { it is ReportRow.Title || it is ReportRow.DateLine })
    }

    @Test
    fun `buildMonthly asset lines have no asset index`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(account("acc1", "Cash", "asset")),
            balancesByAccountId = mapOf("acc1" to 10_000L)
        )
        val accountLines = result.filterIsInstance<ReportRow.AccountLine>()
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
                ReportRow.SectionHeader("Assets"),
                ReportRow.AccountLine("Cash", 10_000),
                ReportRow.TotalLine("Total Assets", 10_000, emphasized = true),
                ReportRow.SectionHeader("Liabilities"),
                ReportRow.AccountLine("Loan", 200),
                ReportRow.TotalLine("Total Liabilities", 200, emphasized = true),
                ReportRow.SectionHeader("Equity"),
                ReportRow.SubsectionHeader("Original Equity"),
                ReportRow.AccountLine("Owner Capital", 500),
                ReportRow.TotalLine("Total Original Equity", 500),
                ReportRow.SubsectionHeader("Unclosed Income Statement accounts"),
                ReportRow.AccountLine("Income", 300, arPrefixed = true),
                ReportRow.AccountLine("Expense", 150, contra = true, arPrefixed = true),
                ReportRow.AccountLine("Gain", 80, arPrefixed = true),
                ReportRow.AccountLine("Loss", 30, contra = true, arPrefixed = true),
                ReportRow.TotalLine("Total Unclosed IS accounts", 200),
                ReportRow.SubsectionHeader("Drawing"),
                ReportRow.AccountLine("Owner Drawing", 60, contra = true),
                ReportRow.TotalLine("Total Drawing", 60, contra = true),
                ReportRow.TotalLine("Total Equity", 640, emphasized = true)
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

        assertTrue(result.none { it is ReportRow.TotalLine && it.label == "Total Changes in Equity" })
    }

    @Test
    fun `buildMonthly omits Unclosed Income Statement accounts subsection when empty`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(account("e", "Owner Capital", "equity")),
            balancesByAccountId = mapOf("e" to 500L)
        )

        assertTrue(result.none { it is ReportRow.SubsectionHeader && it.title == "Unclosed Income Statement accounts" })
    }

    @Test
    fun `buildMonthly Equity section appears when only unclosed IS or drawing accounts exist`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(account("d", "Owner Drawing", "drawing")),
            balancesByAccountId = mapOf("d" to 60L)
        )

        assertTrue(result.any { it is ReportRow.SectionHeader && it.title == "Equity" })
        val totalEquity = result.filterIsInstance<ReportRow.TotalLine>().single { it.label == "Total Equity" }
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
                ReportRow.SectionHeader("Assets"),
                ReportRow.AccountLine("Bank", 15_000),
                ReportRow.AccountLine("Other", 6_500),
                ReportRow.TotalLine("Total Assets", 21_500, emphasized = true)
            ),
            result
        )
    }
}
