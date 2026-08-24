package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance

object BalanceSheetBuilder {

    private const val OTHER_ASSET_THRESHOLD = 10_000L

    fun build(accounts: List<Account>, balances: List<AccountBalance>): List<BalanceSheetRow> {
        val accountMap = accounts.associateBy { it.id }
        val includedBalances = balances.filter { accountMap.containsKey(it.accountId) }
        if (includedBalances.isEmpty()) return emptyList()

        fun linesFor(type: String): List<NamedBalance> =
            linesFor(accountMap, includedBalances, type)

        val assetLines = linesFor("asset").sortedByDescending { it.balance }
        val liabilityLines = linesFor("liability")
        val equityLines = linesFor("equity")
        val incomeLines = linesFor("revenue").sortedByDescending { it.balance }
        val expenseLines = linesFor("expense").sortedByDescending { it.balance }
        val gainLines = linesFor("gain")
        val lossLines = linesFor("loss")
        val drawingLines = linesFor("drawing")

        val totalAssets = assetLines.sumOf { it.balance }
        val totalLiabilities = liabilityLines.sumOf { it.balance }
        val totalOriginalEquity = equityLines.sumOf { it.balance }
        val totalIncome = incomeLines.sumOf { it.balance }
        val totalExpense = expenseLines.sumOf { it.balance }
        val totalGain = gainLines.sumOf { it.balance }
        val totalLoss = lossLines.sumOf { it.balance }
        val totalDrawing = drawingLines.sumOf { it.balance }
        val totalEquity = totalOriginalEquity + totalIncome - totalExpense + totalGain - totalLoss - totalDrawing

        val rows = mutableListOf<BalanceSheetRow>()
        rows += BalanceSheetRow.Title("Instant Balance Sheet")

        if (assetLines.isNotEmpty()) {
            val (mainAssetLines, otherAssetLines) = assetLines.partition {
                Math.abs(it.balance) >= OTHER_ASSET_THRESHOLD
            }

            rows += BalanceSheetRow.SectionHeader("Assets")
            mainAssetLines.forEachIndexed { index, line ->
                rows += BalanceSheetRow.AccountLine(line.name, line.balance, assetIndex = index)
            }
            if (otherAssetLines.isNotEmpty()) {
                rows += BalanceSheetRow.AccountLine(
                    "Other",
                    otherAssetLines.sumOf { it.balance },
                    assetIndex = mainAssetLines.size
                )
            }
            rows += BalanceSheetRow.TotalLine("Total Assets", totalAssets, emphasized = true)
        }

        if (liabilityLines.isNotEmpty()) {
            rows += BalanceSheetRow.SectionHeader("Liabilities")
            liabilityLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance) }
            rows += BalanceSheetRow.TotalLine("Total Liabilities", totalLiabilities, emphasized = true)
        }

        val hasEquitySection = listOf(
            equityLines, incomeLines, expenseLines, gainLines, lossLines, drawingLines
        ).any { it.isNotEmpty() }

        if (hasEquitySection) {
            rows += BalanceSheetRow.SectionHeader("Equity")

            if (equityLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Original Equity")
                equityLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance) }
                rows += BalanceSheetRow.TotalLine("Total Original Equity", totalOriginalEquity)
            }
            if (incomeLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Income")
                incomeLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance) }
                rows += BalanceSheetRow.TotalLine("Total Income", totalIncome)
            }
            if (expenseLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Expense")
                expenseLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance, contra = true) }
                rows += BalanceSheetRow.TotalLine("Total Expense", totalExpense, contra = true)
            }
            if (gainLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Gain")
                gainLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance) }
                rows += BalanceSheetRow.TotalLine("Total Gain", totalGain)
            }
            if (lossLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Loss")
                lossLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance, contra = true) }
                rows += BalanceSheetRow.TotalLine("Total Loss", totalLoss, contra = true)
            }
            if (drawingLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Drawing")
                drawingLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance, contra = true) }
                rows += BalanceSheetRow.TotalLine("Total Drawing", totalDrawing, contra = true)
            }

            val hasChangesInEquity = listOf(
                incomeLines, expenseLines, gainLines, lossLines, drawingLines
            ).any { it.isNotEmpty() }
            if (hasChangesInEquity) {
                val totalChangesInEquity = totalIncome - totalExpense + totalGain - totalLoss - totalDrawing
                rows += BalanceSheetRow.TotalLine(
                    "Total Changes in Equity",
                    totalChangesInEquity,
                    contra = true
                )
            }

            rows += BalanceSheetRow.TotalLine("Total Equity", totalEquity, emphasized = true)
        }

        val balanceDate = includedBalances.maxOf { it.updatedAt }
        rows += BalanceSheetRow.DateLine(balanceDate)

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
    fun buildMonthly(accounts: List<Account>, balancesByAccountId: Map<String, Long>): List<BalanceSheetRow> {
        val accountMap = accounts.associateBy { it.id }

        fun linesFor(type: String): List<NamedBalance> =
            linesFor(accountMap, balancesByAccountId, type)

        val assetLines = linesFor("asset").sortedByDescending { it.balance }
        val liabilityLines = linesFor("liability")
        val equityLines = linesFor("equity")
        val incomeLines = linesFor("revenue").sortedByDescending { it.balance }
        val expenseLines = linesFor("expense").sortedByDescending { it.balance }
        val gainLines = linesFor("gain")
        val lossLines = linesFor("loss")
        val drawingLines = linesFor("drawing")

        val totalAssets = assetLines.sumOf { it.balance }
        val totalLiabilities = liabilityLines.sumOf { it.balance }
        val totalOriginalEquity = equityLines.sumOf { it.balance }
        val totalIncome = incomeLines.sumOf { it.balance }
        val totalExpense = expenseLines.sumOf { it.balance }
        val totalGain = gainLines.sumOf { it.balance }
        val totalLoss = lossLines.sumOf { it.balance }
        val totalDrawing = drawingLines.sumOf { it.balance }
        val totalEquity = totalOriginalEquity + totalIncome - totalExpense + totalGain - totalLoss - totalDrawing

        val rows = mutableListOf<BalanceSheetRow>()

        if (assetLines.isNotEmpty()) {
            val (mainAssetLines, otherAssetLines) = assetLines.partition {
                Math.abs(it.balance) >= OTHER_ASSET_THRESHOLD
            }

            rows += BalanceSheetRow.SectionHeader("Assets")
            mainAssetLines.forEach { line ->
                rows += BalanceSheetRow.AccountLine(line.name, line.balance)
            }
            if (otherAssetLines.isNotEmpty()) {
                rows += BalanceSheetRow.AccountLine("Other", otherAssetLines.sumOf { it.balance })
            }
            rows += BalanceSheetRow.TotalLine("Total Assets", totalAssets, emphasized = true)
        }

        if (liabilityLines.isNotEmpty()) {
            rows += BalanceSheetRow.SectionHeader("Liabilities")
            liabilityLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance) }
            rows += BalanceSheetRow.TotalLine("Total Liabilities", totalLiabilities, emphasized = true)
        }

        val hasUnclosedIsAccounts = listOf(incomeLines, expenseLines, gainLines, lossLines).any { it.isNotEmpty() }
        val hasEquitySection = listOf(equityLines, drawingLines).any { it.isNotEmpty() } || hasUnclosedIsAccounts

        if (hasEquitySection) {
            rows += BalanceSheetRow.SectionHeader("Equity")

            if (equityLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Original Equity")
                equityLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance) }
                rows += BalanceSheetRow.TotalLine("Total Original Equity", totalOriginalEquity)
            }

            if (hasUnclosedIsAccounts) {
                rows += BalanceSheetRow.SubsectionHeader("Unclosed Income Statement accounts")
                if (incomeLines.isNotEmpty()) {
                    rows += BalanceSheetRow.AccountLine("Income", totalIncome, arPrefixed = true)
                }
                if (expenseLines.isNotEmpty()) {
                    rows += BalanceSheetRow.AccountLine("Expense", totalExpense, contra = true, arPrefixed = true)
                }
                if (gainLines.isNotEmpty()) {
                    rows += BalanceSheetRow.AccountLine("Gain", totalGain, arPrefixed = true)
                }
                if (lossLines.isNotEmpty()) {
                    rows += BalanceSheetRow.AccountLine("Loss", totalLoss, contra = true, arPrefixed = true)
                }
                rows += BalanceSheetRow.TotalLine(
                    "Total Unclosed IS accounts",
                    totalIncome - totalExpense + totalGain - totalLoss
                )
            }

            if (drawingLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Drawing")
                drawingLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance, contra = true) }
                rows += BalanceSheetRow.TotalLine("Total Drawing", totalDrawing, contra = true)
            }

            rows += BalanceSheetRow.TotalLine("Total Equity", totalEquity, emphasized = true)
        }

        return rows
    }
}
