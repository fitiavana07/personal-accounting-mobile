package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.LiquidityLevels
import dev.fitiavana.accounting.features.balances.AccountBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceSheetBuilderTest {

    private fun account(id: String, name: String, type: String, liquidityLevel: String? = null) =
        Account(id = id, name = name, type = type, liquidityLevel = liquidityLevel)

    private fun balance(
        accountId: String,
        balance: Long,
        updatedAt: Long = 0L
    ) =
        AccountBalance(
            accountId = accountId,
            balance = balance,
            updatedAt = updatedAt,
            createdAt = updatedAt
        )

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
                ReportRow.SubsectionHeader("Unclassified", assetIndex = 0),
                ReportRow.AccountLine("Cash", 10_000, assetIndex = 0),
                ReportRow.TotalLine("Subtotal", 10_000),
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
        assertEquals(
            listOf("Alpha Bank", "Zebra Bank"),
            accountLines.map { it.name })
    }

    @Test
    fun `only Total Assets is emphasized`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(account("a", "Cash", "asset")),
            balances = listOf(balance("a", 1000))
        )

        val totals = result.filterIsInstance<ReportRow.TotalLine>()
        assertEquals(
            listOf("Total Assets"),
            totals.filter { it.emphasized }.map { it.label })
    }

    @Test
    fun `assets are grouped by liquidity level in LiquidityLevels order, unclassified last`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("a", "Brokerage", "asset", liquidityLevel = LiquidityLevels.STOCKS),
                account("b", "Bank", "asset", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
                account("c", "Piggy Bank", "asset", liquidityLevel = null)
            ),
            balances = listOf(
                balance("a", 500_000),
                balance("b", 200_000),
                balance("c", 50_000)
            )
        )

        assertEquals(
            listOf(
                ReportRow.Title("ASSETS"),
                ReportRow.SubsectionHeader("Cash & Cash Equivalents", assetIndex = 0),
                ReportRow.AccountLine("Bank", 200_000, assetIndex = 1),
                ReportRow.TotalLine("Subtotal", 200_000),
                ReportRow.SubsectionHeader("Stocks", assetIndex = 1),
                ReportRow.AccountLine("Brokerage", 500_000, assetIndex = 0),
                ReportRow.TotalLine("Subtotal", 500_000),
                ReportRow.SubsectionHeader("Unclassified", assetIndex = 2),
                ReportRow.AccountLine("Piggy Bank", 50_000, assetIndex = 2),
                ReportRow.TotalLine("Subtotal", 50_000),
                ReportRow.TotalLine("Total Assets", 750_000, emphasized = true),
                ReportRow.DateLine(0L)
            ),
            result
        )
    }

    @Test
    fun `account line asset index matches the Assets pie chart's global balance ranking, not liquidity group order`() {
        // Brokerage has the largest balance overall but sits in a liquidity
        // group processed after Cash & Cash Equivalents — its color dot must
        // still be index 0, matching AssetSliceBuilder's flat balance sort,
        // not the sequential per-group order BalanceSheetBuilder renders in.
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("a", "Brokerage", "asset", liquidityLevel = LiquidityLevels.STOCKS),
                account("b", "Bank", "asset", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
                account("c", "Piggy Bank", "asset", liquidityLevel = null)
            ),
            balances = listOf(
                balance("a", 500_000),
                balance("b", 200_000),
                balance("c", 50_000)
            )
        )

        val accountLines = result.filterIsInstance<ReportRow.AccountLine>()
        assertEquals(
            mapOf("Brokerage" to 0, "Bank" to 1, "Piggy Bank" to 2),
            accountLines.associate { it.name to it.assetIndex }
        )
    }

    @Test
    fun `account lines below the Other threshold share one asset index across liquidity groups`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("a", "Bank", "asset", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
                account("b", "Petty Cash", "asset", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
                account("c", "Spare Change", "asset", liquidityLevel = LiquidityLevels.STOCKS)
            ),
            balances = listOf(
                balance("a", 15_000),
                balance("b", 4_000),
                balance("c", 2_500)
            )
        )

        val accountLines = result.filterIsInstance<ReportRow.AccountLine>()
        assertEquals(0, accountLines.single { it.name == "Bank" }.assetIndex)
        assertEquals(1, accountLines.single { it.name == "Petty Cash" }.assetIndex)
        assertEquals(1, accountLines.single { it.name == "Spare Change" }.assetIndex)
    }

    @Test
    fun `liquidity level group headers are indexed by their render order, skipping empty groups`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("a", "Brokerage", "asset", liquidityLevel = LiquidityLevels.STOCKS),
                account("b", "Piggy Bank", "asset", liquidityLevel = null)
            ),
            balances = listOf(balance("a", 500_000), balance("b", 50_000))
        )

        val headers = result.filterIsInstance<ReportRow.SubsectionHeader>()
        assertEquals(
            listOf("Stocks" to 0, "Unclassified" to 1),
            headers.map { it.title to it.assetIndex }
        )
    }

    @Test
    fun `liquidity level groups with no accounts are omitted`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("a", "Bank", "asset", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS)
            ),
            balances = listOf(balance("a", 100_000))
        )

        val headers = result.filterIsInstance<ReportRow.SubsectionHeader>()
        assertEquals(listOf("Cash & Cash Equivalents"), headers.map { it.title })
    }

    @Test
    fun `accounts within a liquidity level group are sorted by balance decreasing`() {
        val result = BalanceSheetBuilder.build(
            accounts = listOf(
                account("a", "Zebra Bank", "asset", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
                account("b", "Alpha Bank", "asset", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS)
            ),
            balances = listOf(balance("a", 10_000), balance("b", 20_000))
        )

        val accountLines = result.filterIsInstance<ReportRow.AccountLine>()
        assertEquals(listOf("Alpha Bank", "Zebra Bank"), accountLines.map { it.name })
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

        val totalAssets = result.filterIsInstance<ReportRow.TotalLine>()
            .single { it.label == "Total Assets" }
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
        assertTrue(
            result.filterIsInstance<ReportRow.AccountLine>()
                .none { it.name.isEmpty() })
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
                ReportRow.AccountLine("Cash", 10_000, accountId = "a"),
                ReportRow.TotalLine("Total Assets", 10_000, emphasized = true),
                ReportRow.SectionHeader("Liabilities"),
                ReportRow.AccountLine("Loan", 200, accountId = "l"),
                ReportRow.TotalLine(
                    "Total Liabilities",
                    200,
                    emphasized = true
                ),
                ReportRow.SectionHeader("Equity"),
                ReportRow.SubsectionHeader("Original Equity"),
                ReportRow.AccountLine("Owner Capital", 500, accountId = "e"),
                ReportRow.TotalLine("Total Original Equity", 500),
                ReportRow.SubsectionHeader("Unclosed Income Statement accounts"),
                ReportRow.AccountLine("Income", 300),
                ReportRow.AccountLine("Expense", 150, contra = true),
                ReportRow.AccountLine("Gain", 80),
                ReportRow.AccountLine("Loss", 30, contra = true),
                ReportRow.TotalLine(
                    "Total Unclosed IS accounts",
                    200,
                    parenthesizeNegative = true
                ),
                ReportRow.SubsectionHeader("Drawing"),
                ReportRow.AccountLine("Owner Drawing", 60, contra = true),
                ReportRow.TotalLine("Total Drawing", 60, contra = true),
                ReportRow.TotalLine("Total Equity", 640, emphasized = true)
            ),
            result
        )
    }

    @Test
    fun `buildMonthly Total Unclosed IS accounts is negative when expense exceeds income`() {
        val accounts = listOf(
            account("r", "Salary", "revenue"),
            account("x", "Rent", "expense")
        )
        val balances = mapOf("r" to 100L, "x" to 300L)

        val result = BalanceSheetBuilder.buildMonthly(accounts, balances)

        val totalUnclosedIs = result.filterIsInstance<ReportRow.TotalLine>()
            .single { it.label == "Total Unclosed IS accounts" }
        assertEquals(-200L, totalUnclosedIs.amount)
        assertTrue(totalUnclosedIs.parenthesizeNegative)
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
        val totalEquity = result.filterIsInstance<ReportRow.TotalLine>()
            .single { it.label == "Total Equity" }
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
            balancesByAccountId = mapOf(
                "a" to 15_000L,
                "b" to 4_000L,
                "c" to 2_500L
            )
        )

        assertEquals(
            listOf(
                ReportRow.SectionHeader("Assets"),
                ReportRow.AccountLine("Bank", 15_000, accountId = "a"),
                ReportRow.AccountLine("Other", 6_500),
                ReportRow.TotalLine("Total Assets", 21_500, emphasized = true)
            ),
            result
        )
    }

    // --- totalEquity ---

    @Test
    fun `totalEquity folds original equity, unclosed income statement accounts and drawing`() {
        val accounts = listOf(
            account("e", "Owner Capital", "equity"),
            account("r", "Salary", "revenue"),
            account("x", "Rent", "expense"),
            account("g", "Stock Gain", "gain"),
            account("o", "Stock Loss", "loss"),
            account("d", "Owner Drawing", "drawing")
        )
        val balances = mapOf(
            "e" to 500L, "r" to 300L, "x" to 150L, "g" to 80L, "o" to 30L, "d" to 60L
        )

        // 500 + 300 - 150 + 80 - 30 - 60 = 640
        assertEquals(640L, BalanceSheetBuilder.totalEquity(accounts, balances))
    }

    @Test
    fun `totalEquity ignores asset and liability accounts`() {
        val accounts = listOf(
            account("a", "Cash", "asset"),
            account("l", "Loan", "liability")
        )
        val balances = mapOf("a" to 10_000L, "l" to 5_000L)

        assertEquals(0L, BalanceSheetBuilder.totalEquity(accounts, balances))
    }

    @Test
    fun `totalEquity matches the Total Equity line produced by buildMonthly`() {
        val accounts = listOf(
            account("e", "Owner Capital", "equity"),
            account("r", "Salary", "revenue"),
            account("d", "Owner Drawing", "drawing")
        )
        val balances = mapOf("e" to 500L, "r" to 300L, "d" to 60L)

        val totalEquityLine = BalanceSheetBuilder.buildMonthly(accounts, balances)
            .filterIsInstance<ReportRow.TotalLine>()
            .single { it.label == "Total Equity" }
            .amount

        assertEquals(totalEquityLine, BalanceSheetBuilder.totalEquity(accounts, balances))
    }

    // --- unclosedIsBalance ---

    @Test
    fun `unclosedIsBalance folds income, expense, gain and loss`() {
        val accounts = listOf(
            account("r", "Salary", "revenue"),
            account("x", "Rent", "expense"),
            account("g", "Stock Gain", "gain"),
            account("o", "Stock Loss", "loss")
        )
        val balances = mapOf("r" to 300L, "x" to 150L, "g" to 80L, "o" to 30L)

        // 300 - 150 + 80 - 30 = 200
        assertEquals(200L, BalanceSheetBuilder.unclosedIsBalance(accounts, balances))
    }

    @Test
    fun `unclosedIsBalance is negative when expense exceeds income`() {
        val accounts = listOf(
            account("r", "Salary", "revenue"),
            account("x", "Rent", "expense")
        )
        val balances = mapOf("r" to 100L, "x" to 300L)

        assertEquals(-200L, BalanceSheetBuilder.unclosedIsBalance(accounts, balances))
    }

    @Test
    fun `unclosedIsBalance ignores asset, liability, equity and drawing accounts`() {
        val accounts = listOf(
            account("a", "Cash", "asset"),
            account("e", "Owner Capital", "equity"),
            account("d", "Owner Drawing", "drawing")
        )
        val balances = mapOf("a" to 10_000L, "e" to 500L, "d" to 60L)

        assertEquals(0L, BalanceSheetBuilder.unclosedIsBalance(accounts, balances))
    }

    @Test
    fun `unclosedIsBalance matches the Total Unclosed IS accounts line produced by buildMonthly`() {
        val accounts = listOf(
            account("r", "Salary", "revenue"),
            account("x", "Rent", "expense")
        )
        val balances = mapOf("r" to 300L, "x" to 150L)

        val totalUnclosedIsLine = BalanceSheetBuilder.buildMonthly(accounts, balances)
            .filterIsInstance<ReportRow.TotalLine>()
            .single { it.label == "Total Unclosed IS accounts" }
            .amount

        assertEquals(totalUnclosedIsLine, BalanceSheetBuilder.unclosedIsBalance(accounts, balances))
    }

    // --- buildMonthly: native (instrument / intermediary) amounts for tap-to-expand ---

    @Test
    fun `buildMonthly attaches accountId and native amounts to per-account lines while totals stay in base`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(
                account("a1", "Bybit", "asset"),
                account("a2", "Cash", "asset"),
                account("l1", "Loan", "liability"),
                account("e1", "Capital", "equity")
            ),
            balancesByAccountId = mapOf("a1" to 50_000L, "a2" to 20_000L, "l1" to 5_000L, "e1" to 65_000L),
            nativeByAccountId = mapOf(
                "a1" to listOf(NativeAmount(11L, "USDT"), NativeAmount(10L, "USDC")),
                "l1" to listOf(NativeAmount(2L, "EUR")),
                "e1" to listOf(NativeAmount(30L, "USD"))
            )
        )

        assertEquals(
            listOf(
                ReportRow.SectionHeader("Assets"),
                ReportRow.AccountLine(
                    "Bybit", 50_000L, accountId = "a1",
                    nativeAmounts = listOf(NativeAmount(11L, "USDT"), NativeAmount(10L, "USDC"))
                ),
                ReportRow.AccountLine("Cash", 20_000L, accountId = "a2"),
                ReportRow.TotalLine("Total Assets", 70_000L, emphasized = true),
                ReportRow.SectionHeader("Liabilities"),
                ReportRow.AccountLine("Loan", 5_000L, accountId = "l1", nativeAmounts = listOf(NativeAmount(2L, "EUR"))),
                ReportRow.TotalLine("Total Liabilities", 5_000L, emphasized = true),
                ReportRow.SectionHeader("Equity"),
                ReportRow.SubsectionHeader("Original Equity"),
                ReportRow.AccountLine("Capital", 65_000L, accountId = "e1", nativeAmounts = listOf(NativeAmount(30L, "USD"))),
                ReportRow.TotalLine("Total Original Equity", 65_000L),
                ReportRow.TotalLine("Total Equity", 65_000L, emphasized = true)
            ),
            result
        )
    }

    @Test
    fun `buildMonthly attaches an account's APR to its line`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(
                Account(id = "a1", name = "Earn", type = "asset", aprPercent = 5.5),
                account("a2", "Cash", "asset")
            ),
            balancesByAccountId = mapOf("a1" to 50_000L, "a2" to 20_000L)
        )

        assertTrue(result.contains(ReportRow.AccountLine("Earn", 50_000L, accountId = "a1", aprPercent = 5.5)))
        assertTrue(result.contains(ReportRow.AccountLine("Cash", 20_000L, accountId = "a2")))
    }

    @Test
    fun `buildMonthly keeps the lumped Other line and category lines in base without native amounts`() {
        val result = BalanceSheetBuilder.buildMonthly(
            accounts = listOf(
                account("a1", "Small", "asset"),
                account("x1", "Food", "expense")
            ),
            balancesByAccountId = mapOf("a1" to 500L, "x1" to 100L),
            nativeByAccountId = mapOf(
                "a1" to listOf(NativeAmount(5L, "USDT")),
                "x1" to listOf(NativeAmount(1L, "USDT"))
            )
        )

        assertTrue(result.contains(ReportRow.AccountLine("Other", 500L)))
        assertTrue(result.contains(ReportRow.AccountLine("Expense", 100L, contra = true)))
    }

    @Test
    fun `buildMonthly without native amounts only adds accountId to lines`() {
        val result = BalanceSheetBuilder.buildMonthly(
            listOf(account("a1", "Cash", "asset")),
            mapOf("a1" to 20_000L)
        )

        assertEquals(ReportRow.AccountLine("Cash", 20_000L, accountId = "a1"), result[1])
    }
}
