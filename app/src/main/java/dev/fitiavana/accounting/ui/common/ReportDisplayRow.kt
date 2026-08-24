package dev.fitiavana.accounting.ui.common

sealed class ReportDisplayRow {
    data class Title(val text: String) : ReportDisplayRow()
    data class SectionHeader(val title: String) : ReportDisplayRow()
    data class SubsectionHeader(val title: String) : ReportDisplayRow()
    data class AccountLine(val name: String, val amountText: String, val color: Int? = null) : ReportDisplayRow()
    data class TotalLine(
        val label: String,
        val amountText: String,
        val emphasized: Boolean = false
    ) : ReportDisplayRow()
    data class DateLine(val text: String) : ReportDisplayRow()
}
