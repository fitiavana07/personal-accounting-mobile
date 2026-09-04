package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account

/**
 * Labels for the "Unclosed Income Statement accounts" concept (Income,
 * Expense, Gain and Loss accounts not yet closed into equity). [SHORT] is
 * this report's column title; [FULL] is kept here for future reuse
 * elsewhere. Unrelated to the existing hardcoded strings in
 * [BalanceSheetBuilder] ("Unclosed Income Statement accounts" subsection
 * header, "Total Unclosed IS accounts" total label), which are left as-is.
 */
object UnclosedIsAccountsLabels {
    const val SHORT = "Unclosed IS Accounts"
    const val FULL = "Unclosed Income Statement Accounts"
}

/** Statement of Changes in Equity, as a matrix: [columnTitles] (one per equity account, plus
 * "Unclosed IS Accounts", "Drawing" and "Total") and [rows] whose cells align with those columns. */
data class EquityStatement(
    val columnTitles: List<String>,
    val rows: List<EquityStatementRow>
)

/**
 * One row of [EquityStatement]: a [label] plus one raw signed amount per
 * data column, in the same order as [EquityStatement.columnTitles]. A null
 * cell means "not relevant here" (displayed as "-"), and the last cell is
 * always the Total column (the sum of the other cells, nulls counting as 0).
 */
data class EquityStatementRow(
    val label: String,
    val cells: List<Long?>,
    val emphasized: Boolean = false
)

/**
 * Unlike [BalanceSheetBuilder]/[IncomeStatementBuilder], this report is a
 * matrix (multiple value columns per row), so it can't reuse [ReportRow].
 *
 * Sign convention: Drawing amounts (both balances and changes) are stored
 * negative here, so the Total column of every row is a plain sum of that
 * row's cells (see [EquityStatementRow]).
 */
object EquityStatementBuilder {

    fun build(
        accounts: List<Account>,
        previousMonthEndBalances: Map<String, Long>,
        periodChangeBalances: Map<String, Long>,
        previousBalanceLabel: String,
        currentBalanceLabel: String
    ): EquityStatement {
        val equityAccounts = accounts.filter { it.type == "equity" }.sortedBy { it.name }
        val drawingAccountIds = accounts.filter { it.type == "drawing" }.map { it.id }

        val columnTitles = equityAccounts.map { it.name } +
            listOf(UnclosedIsAccountsLabels.SHORT, "Drawing", "Total")

        val previousCells: List<Long> = equityAccounts.map { previousMonthEndBalances[it.id] ?: 0L } +
            listOf(
                BalanceSheetBuilder.unclosedIsBalance(accounts, previousMonthEndBalances),
                -drawingAccountIds.sumOf { previousMonthEndBalances[it] ?: 0L }
            )

        val changeCells: List<Long> = equityAccounts.map { periodChangeBalances[it.id] ?: 0L } +
            listOf(
                IncomeStatementBuilder.netIncome(accounts, periodChangeBalances),
                -drawingAccountIds.sumOf { periodChangeBalances[it] ?: 0L }
            )

        val currentCells: List<Long> = previousCells.indices.map { previousCells[it] + changeCells[it] }

        val changeLabels = equityAccounts.map { "Changes in ${it.name}" } +
            listOf("Changes in Unclosed IS Accounts", "Changes in Drawing")

        val rows = mutableListOf<EquityStatementRow>()
        rows += totalledRow(previousBalanceLabel, previousCells, emphasized = true)
        for (index in changeLabels.indices) {
            rows += totalledRow(
                changeLabels[index],
                List(changeCells.size) { i -> if (i == index) changeCells[index] else null }
            )
        }
        rows += totalledRow(currentBalanceLabel, currentCells, emphasized = true)

        return EquityStatement(columnTitles, rows)
    }

    private fun totalledRow(
        label: String,
        cells: List<Long?>,
        emphasized: Boolean = false
    ): EquityStatementRow =
        EquityStatementRow(label, cells + cells.sumOf { it ?: 0L }, emphasized)
}
