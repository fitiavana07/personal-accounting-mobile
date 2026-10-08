package dev.fitiavana.accounting.ui.common

sealed class ReportDisplayRow {
    data class Title(val text: String) : ReportDisplayRow()
    data class SectionHeader(val title: String) : ReportDisplayRow()
    data class SubsectionHeader(val title: String, val color: Int? = null) : ReportDisplayRow()
    data class AccountLine(
        val name: String,
        val amountText: String,
        val color: Int? = null,
        // Only set on Balance Sheet lines that can be tapped to reveal native balances.
        val accountId: String? = null,
        val expandable: Boolean = false,
        val expanded: Boolean = false
    ) : ReportDisplayRow()

    /** Sub-row under an expanded [AccountLine]: balance in one instrument, labeled by its [code]. */
    data class NativeLine(val code: String, val amountText: String) : ReportDisplayRow()

    /** Sub-row under an expanded [AccountLine] of an Earn account: its yearly rate, e.g. "5.5% ". */
    data class AprLine(val amountText: String) : ReportDisplayRow()
    data class TotalLine(
        val label: String,
        val amountText: String,
        val emphasized: Boolean = false
    ) : ReportDisplayRow()
    data class DateLine(val text: String) : ReportDisplayRow()
}
