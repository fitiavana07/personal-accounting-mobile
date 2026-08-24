package dev.fitiavana.accounting.features.reports

/**
 * Raw report row: amounts are unformatted [Long]s and `contra`/`arPrefixed` are display-convention
 * hints (parens for contra amounts, "Ar" prefix), not formatted text. A ui/ presentation step turns
 * these into display strings and colors. Shared by [BalanceSheetBuilder] and [IncomeStatementBuilder].
 */
sealed class ReportRow {
    data class Title(val text: String) : ReportRow()
    data class SectionHeader(val title: String) : ReportRow()
    data class SubsectionHeader(val title: String) : ReportRow()
    data class AccountLine(
        val name: String,
        val amount: Long,
        val contra: Boolean = false,
        val arPrefixed: Boolean = false,
        val assetIndex: Int? = null
    ) : ReportRow()

    data class TotalLine(
        val label: String,
        val amount: Long,
        val emphasized: Boolean = false,
        val contra: Boolean = false
    ) : ReportRow()

    data class DateLine(val timestampMs: Long) : ReportRow()
}
