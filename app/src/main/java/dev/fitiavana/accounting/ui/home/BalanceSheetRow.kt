package dev.fitiavana.accounting.ui.home

sealed class BalanceSheetRow {
    data class Title(val text: String) : BalanceSheetRow()
    data class SectionHeader(val title: String) : BalanceSheetRow()
    data class SubsectionHeader(val title: String) : BalanceSheetRow()
    data class AccountLine(val name: String, val amountText: String, val color: Int? = null) : BalanceSheetRow()
    data class TotalLine(
        val label: String,
        val amountText: String,
        val emphasized: Boolean = false
    ) : BalanceSheetRow()
    data class DateLine(val text: String) : BalanceSheetRow()
}
