package dev.fitiavana.accounting.ui.reports

import dev.fitiavana.accounting.R

enum class ReportType(val label: String, val shortLabel: String, val iconRes: Int) {
    BALANCE_SHEET("Balance Sheet", "Balance Sheet", R.drawable.ic_report_balance_sheet),
    INCOME_STATEMENT("Income Statement", "Income", R.drawable.ic_report_income_statement),
    CHANGES_IN_EQUITY("Statement of Changes in Equity", "Equity", R.drawable.ic_report_equity_statement)
}
