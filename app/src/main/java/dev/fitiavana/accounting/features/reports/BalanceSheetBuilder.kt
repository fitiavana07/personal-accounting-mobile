package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.LiquidityLevels
import dev.fitiavana.accounting.features.balances.AccountBalance
import kotlin.math.abs

object BalanceSheetBuilder {

    private data class NamedLiquidAmount(
        val accountId: String,
        val name: String,
        val amount: Long,
        val liquidityLevel: String?
    )

    /** Home tab's "Instant Balance Sheet" card: assets only, grouped by liquidity level, no liabilities/equity. */
    fun build(
        accounts: List<Account>,
        balances: List<AccountBalance>
    ): List<ReportRow> {
        val accountMap = accounts.associateBy { it.id }
        val includedBalances =
            balances.filter { accountMap.containsKey(it.accountId) }
        if (includedBalances.isEmpty()) return emptyList()

        val assetLines = includedBalances
            .filter { accountMap.getValue(it.accountId).type == "asset" }
            .filter { it.balance != 0L }
            .map {
                val account = accountMap.getValue(it.accountId)
                NamedLiquidAmount(account.id, account.name, it.balance, account.liquidityLevel)
            }
        val totalAssets = assetLines.sumOf { it.amount }

        // Same color per account as the Home "Assets" pie chart (AssetColorIndex),
        // even though this view groups/orders accounts differently (by liquidity level).
        val colorIndexByAccountId =
            AssetColorIndex.compute(accounts, balances).colorIndexByAccountId()

        val rows = mutableListOf<ReportRow>()
        rows += ReportRow.Title("ASSETS")

        if (assetLines.isNotEmpty()) {
            // Null last: unclassified assets are shown after every known liquidity level.
            val groupOrder = LiquidityLevels.VALUES + listOf<String?>(null)
            var groupIndex = 0
            for (liquidityLevel in groupOrder) {
                val groupLines = assetLines
                    .filter { it.liquidityLevel == liquidityLevel }
                    .sortedByDescending { it.amount }
                if (groupLines.isEmpty()) continue

                rows += ReportRow.SubsectionHeader(
                    LiquidityLevels.displayName(liquidityLevel),
                    assetIndex = groupIndex
                )
                groupIndex++
                groupLines.forEach { line ->
                    rows += ReportRow.AccountLine(
                        line.name,
                        line.amount,
                        assetIndex = colorIndexByAccountId[line.accountId]
                    )
                }
                rows += ReportRow.TotalLine(
                    "Subtotal",
                    groupLines.sumOf { it.amount }
                )
            }
            rows += ReportRow.TotalLine(
                "Total Assets",
                totalAssets,
                emphasized = true
            )
        }

        val balanceDate = includedBalances.maxOf { it.updatedAt }
        rows += ReportRow.DateLine(balanceDate)

        return rows
    }

    /**
     * Monthly Balance Sheet variant: no title/date rows (the caller renders
     * those outside the row list), no asset slice index (no color dots), and
     * Income/Expense/Gain/Loss are collapsed into a single "Unclosed Income
     * Statement accounts" subsection (each becoming one line equal to that
     * category's total) while Drawing stays its own subsection. Total Equity
     * still folds in all of them so Total Assets = Total Liabilities + Total
     * Equity holds; only the intermediate "Total Changes in Equity" line is
     * omitted.
     */
    fun buildMonthly(
        accounts: List<Account>,
        balancesByAccountId: Map<String, Long>
    ): List<ReportRow> {
        val accountMap = accounts.associateBy { it.id }

        fun linesFor(type: String): List<NamedAmount> =
            linesFor(accountMap, balancesByAccountId, type)

        val assetLines = linesFor("asset").sortedByDescending { it.amount }
        val liabilityLines =
            linesFor("liability").sortedByDescending { it.amount }
        val equityLines = linesFor("equity").sortedByDescending { it.amount }
        val incomeLines = linesFor("revenue").sortedByDescending { it.amount }
        val expenseLines = linesFor("expense").sortedByDescending { it.amount }
        val gainLines = linesFor("gain").sortedByDescending { it.amount }
        val lossLines = linesFor("loss").sortedByDescending { it.amount }
        val drawingLines = linesFor("drawing").sortedByDescending { it.amount }

        val totalAssets = assetLines.sumOf { it.amount }
        val totalLiabilities = liabilityLines.sumOf { it.amount }
        val totalOriginalEquity = equityLines.sumOf { it.amount }
        val totalIncome = incomeLines.sumOf { it.amount }
        val totalExpense = expenseLines.sumOf { it.amount }
        val totalGain = gainLines.sumOf { it.amount }
        val totalLoss = lossLines.sumOf { it.amount }
        val totalDrawing = drawingLines.sumOf { it.amount }
        val totalEquity = totalEquityOf(
            totalOriginalEquity,
            totalIncome,
            totalExpense,
            totalGain,
            totalLoss,
            totalDrawing
        )

        val rows = mutableListOf<ReportRow>()

        if (assetLines.isNotEmpty()) {
            val (mainAssetLines, otherAssetLines) = assetLines.partition {
                abs(it.amount) >= AssetColorIndex.OTHER_ASSET_THRESHOLD
            }

            rows += ReportRow.SectionHeader("Assets")
            mainAssetLines.forEach { line ->
                rows += ReportRow.AccountLine(line.name, line.amount)
            }
            if (otherAssetLines.isNotEmpty()) {
                rows += ReportRow.AccountLine(
                    "Other",
                    otherAssetLines.sumOf { it.amount })
            }
            rows += ReportRow.TotalLine(
                "Total Assets",
                totalAssets,
                emphasized = true
            )
        }

        if (liabilityLines.isNotEmpty()) {
            rows += ReportRow.SectionHeader("Liabilities")
            liabilityLines.forEach {
                rows += ReportRow.AccountLine(
                    it.name,
                    it.amount
                )
            }
            rows += ReportRow.TotalLine(
                "Total Liabilities",
                totalLiabilities,
                emphasized = true
            )
        }

        val hasUnclosedIsAccounts = listOf(
            incomeLines,
            expenseLines,
            gainLines,
            lossLines
        ).any { it.isNotEmpty() }

        val hasEquitySection = listOf(
            equityLines,
            drawingLines
        ).any { it.isNotEmpty() } || hasUnclosedIsAccounts

        if (hasEquitySection) {
            rows += ReportRow.SectionHeader("Equity")

            if (equityLines.isNotEmpty()) {
                rows += ReportRow.SubsectionHeader("Original Equity")
                equityLines.forEach {
                    rows += ReportRow.AccountLine(
                        it.name,
                        it.amount
                    )
                }
                rows += ReportRow.TotalLine(
                    "Total Original Equity",
                    totalOriginalEquity
                )
            }

            if (hasUnclosedIsAccounts) {
                rows += ReportRow.SubsectionHeader("Unclosed Income Statement accounts")
                if (incomeLines.isNotEmpty()) {
                    rows += ReportRow.AccountLine(
                        "Income",
                        totalIncome
                    )
                }
                if (expenseLines.isNotEmpty()) {
                    rows += ReportRow.AccountLine(
                        "Expense",
                        totalExpense,
                        contra = true
                    )
                }
                if (gainLines.isNotEmpty()) {
                    rows += ReportRow.AccountLine(
                        "Gain",
                        totalGain
                    )
                }
                if (lossLines.isNotEmpty()) {
                    rows += ReportRow.AccountLine(
                        "Loss",
                        totalLoss,
                        contra = true
                    )
                }
                rows += ReportRow.TotalLine(
                    "Total Unclosed IS accounts",
                    totalIncome - totalExpense + totalGain - totalLoss,
                    parenthesizeNegative = true
                )
            }

            if (drawingLines.isNotEmpty()) {
                rows += ReportRow.SubsectionHeader("Drawing")
                drawingLines.forEach {
                    rows += ReportRow.AccountLine(
                        it.name,
                        it.amount,
                        contra = true
                    )
                }
                rows += ReportRow.TotalLine(
                    "Total Drawing",
                    totalDrawing,
                    contra = true
                )
            }

            rows += ReportRow.TotalLine(
                "Total Equity",
                totalEquity,
                emphasized = true
            )
        }

        return rows
    }

    /** Total Equity as of [balancesByAccountId], same formula as the "Total Equity" line in [buildMonthly]. */
    fun totalEquity(
        accounts: List<Account>,
        balancesByAccountId: Map<String, Long>
    ): Long {
        val accountMap = accounts.associateBy { it.id }

        fun linesFor(type: String): List<NamedAmount> =
            linesFor(accountMap, balancesByAccountId, type)

        return totalEquityOf(
            totalOriginalEquity = linesFor("equity").sumOf { it.amount },
            totalIncome = linesFor("revenue").sumOf { it.amount },
            totalExpense = linesFor("expense").sumOf { it.amount },
            totalGain = linesFor("gain").sumOf { it.amount },
            totalLoss = linesFor("loss").sumOf { it.amount },
            totalDrawing = linesFor("drawing").sumOf { it.amount }
        )
    }

    /** Shared "Total Equity" formula used by both [buildMonthly] and [totalEquity]. */
    private fun totalEquityOf(
        totalOriginalEquity: Long,
        totalIncome: Long,
        totalExpense: Long,
        totalGain: Long,
        totalLoss: Long,
        totalDrawing: Long
    ): Long =
        totalOriginalEquity + totalIncome - totalExpense + totalGain - totalLoss - totalDrawing
}
