package dev.fitiavana.accounting.ui.common

import dev.fitiavana.accounting.features.reports.EquityStatement

/** Turns a raw [EquityStatement] (unformatted amounts) into a display-ready [EquityStatementDisplay]. */
object EquityStatementPresenter {

    fun present(statement: EquityStatement): EquityStatementDisplay = EquityStatementDisplay(
        statement.columnTitles,
        statement.rows.map { row ->
            EquityStatementDisplayRow(
                row.label,
                row.cells.map { formatCell(it) },
                row.emphasized
            )
        }
    )

    /**
     * A null cell means "not relevant here" (a dash literal, not a formatted
     * amount, per the "-" placeholder convention for this report). A
     * negative amount is parenthesized, matching the contra/parenthesizeNegative
     * convention used by [ReportPresenter] for the other reports.
     */
    private fun formatCell(amount: Long?): String = when {
        amount == null -> "-"
        amount < 0 -> "(${TransactionDisplay.formatAmount(-amount)})"
        else -> TransactionDisplay.formatAmount(amount)
    }
}
