package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance

object BalanceSheetBuilder {

    private const val OTHER_ASSET_THRESHOLD = 10_000L

    /** Home tab's "Instant Balance Sheet" card: assets only, no liabilities/equity. */
    fun build(accounts: List<Account>, balances: List<AccountBalance>): List<ReportRow> {
        val accountMap = accounts.associateBy { it.id }
        val includedBalances = balances.filter { accountMap.containsKey(it.accountId) }
        if (includedBalances.isEmpty()) return emptyList()

        val assetLines = linesFor(accountMap, includedBalances, "asset").sortedByDescending { it.amount }
        val totalAssets = assetLines.sumOf { it.amount }

        val rows = mutableListOf<ReportRow>()
        rows += ReportRow.Title("ASSETS")

        if (assetLines.isNotEmpty()) {
            val (mainAssetLines, otherAssetLines) = assetLines.partition {
                Math.abs(it.amount) >= OTHER_ASSET_THRESHOLD
            }

            mainAssetLines.forEachIndexed { index, line ->
                rows += ReportRow.AccountLine(line.name, line.amount, assetIndex = index)
            }
            if (otherAssetLines.isNotEmpty()) {
                rows += ReportRow.AccountLine(
                    "Other",
                    otherAssetLines.sumOf { it.amount },
                    assetIndex = mainAssetLines.size
                )
            }
            rows += ReportRow.TotalLine("Total Assets", totalAssets, emphasized = true)
        }

        val balanceDate = includedBalances.maxOf { it.updatedAt }
        rows += ReportRow.DateLine(balanceDate)

        return rows
    }

    /**
     * Monthly Balance Sheet variant: no title/date rows (the caller renders those
     * outside the row list), no asset slice index (no color dots), and Income/Expense/Gain/Loss are
     * collapsed into a single "Unclosed Income Statement accounts" subsection
     * (each becoming one line equal to that category's total) while Drawing stays
     * its own subsection. Total Equity still folds in all of them so
     * Total Assets = Total Liabilities + Total Equity holds; only the intermediate
     * "Total Changes in Equity" line is omitted.
     */
    fun buildMonthly(accounts: List<Account>, balancesByAccountId: Map<String, Long>): List<ReportRow> {
        val accountMap = accounts.associateBy { it.id }

        fun linesFor(type: String): List<NamedAmount> =
            linesFor(accountMap, balancesByAccountId, type)

        val assetLines = linesFor("asset").sortedByDescending { it.amount }
        val liabilityLines = linesFor("liability")
        val equityLines = linesFor("equity")
        val incomeLines = linesFor("revenue").sortedByDescending { it.amount }
        val expenseLines = linesFor("expense").sortedByDescending { it.amount }
        val gainLines = linesFor("gain")
        val lossLines = linesFor("loss")
        val drawingLines = linesFor("drawing")

        val totalAssets = assetLines.sumOf { it.amount }
        val totalLiabilities = liabilityLines.sumOf { it.amount }
        val totalOriginalEquity = equityLines.sumOf { it.amount }
        val totalIncome = incomeLines.sumOf { it.amount }
        val totalExpense = expenseLines.sumOf { it.amount }
        val totalGain = gainLines.sumOf { it.amount }
        val totalLoss = lossLines.sumOf { it.amount }
        val totalDrawing = drawingLines.sumOf { it.amount }
        val totalEquity = totalOriginalEquity + totalIncome - totalExpense + totalGain - totalLoss - totalDrawing

        val rows = mutableListOf<ReportRow>()

        if (assetLines.isNotEmpty()) {
            val (mainAssetLines, otherAssetLines) = assetLines.partition {
                Math.abs(it.amount) >= OTHER_ASSET_THRESHOLD
            }

            rows += ReportRow.SectionHeader("Assets")
            mainAssetLines.forEach { line ->
                rows += ReportRow.AccountLine(line.name, line.amount)
            }
            if (otherAssetLines.isNotEmpty()) {
                rows += ReportRow.AccountLine("Other", otherAssetLines.sumOf { it.amount })
            }
            rows += ReportRow.TotalLine("Total Assets", totalAssets, emphasized = true)
        }

        if (liabilityLines.isNotEmpty()) {
            rows += ReportRow.SectionHeader("Liabilities")
            liabilityLines.forEach { rows += ReportRow.AccountLine(it.name, it.amount) }
            rows += ReportRow.TotalLine("Total Liabilities", totalLiabilities, emphasized = true)
        }

        val hasUnclosedIsAccounts = listOf(incomeLines, expenseLines, gainLines, lossLines).any { it.isNotEmpty() }
        val hasEquitySection = listOf(equityLines, drawingLines).any { it.isNotEmpty() } || hasUnclosedIsAccounts

        if (hasEquitySection) {
            rows += ReportRow.SectionHeader("Equity")

            if (equityLines.isNotEmpty()) {
                rows += ReportRow.SubsectionHeader("Original Equity")
                equityLines.forEach { rows += ReportRow.AccountLine(it.name, it.amount) }
                rows += ReportRow.TotalLine("Total Original Equity", totalOriginalEquity)
            }

            if (hasUnclosedIsAccounts) {
                rows += ReportRow.SubsectionHeader("Unclosed Income Statement accounts")
                if (incomeLines.isNotEmpty()) {
                    rows += ReportRow.AccountLine("Income", totalIncome, arPrefixed = true)
                }
                if (expenseLines.isNotEmpty()) {
                    rows += ReportRow.AccountLine("Expense", totalExpense, contra = true, arPrefixed = true)
                }
                if (gainLines.isNotEmpty()) {
                    rows += ReportRow.AccountLine("Gain", totalGain, arPrefixed = true)
                }
                if (lossLines.isNotEmpty()) {
                    rows += ReportRow.AccountLine("Loss", totalLoss, contra = true, arPrefixed = true)
                }
                rows += ReportRow.TotalLine(
                    "Total Unclosed IS accounts",
                    totalIncome - totalExpense + totalGain - totalLoss
                )
            }

            if (drawingLines.isNotEmpty()) {
                rows += ReportRow.SubsectionHeader("Drawing")
                drawingLines.forEach { rows += ReportRow.AccountLine(it.name, it.amount, contra = true) }
                rows += ReportRow.TotalLine("Total Drawing", totalDrawing, contra = true)
            }

            rows += ReportRow.TotalLine("Total Equity", totalEquity, emphasized = true)
        }

        return rows
    }
}
