package dev.fitiavana.accounting.features.reports

/**
 * Raw report row: amounts are unformatted [Long]s and `contra`/`arPrefixed` are display-convention
 * hints (parens for contra amounts, "Ar" prefix), not formatted text. A ui/ presentation step turns
 * these into display strings and colors.
 */
sealed class BalanceSheetRow {
    data class Title(val text: String) : BalanceSheetRow()
    data class SectionHeader(val title: String) : BalanceSheetRow()
    data class SubsectionHeader(val title: String) : BalanceSheetRow()
    data class AccountLine(
        val name: String,
        val amount: Long,
        val contra: Boolean = false,
        val arPrefixed: Boolean = false,
        val assetIndex: Int? = null
    ) : BalanceSheetRow()
    data class TotalLine(
        val label: String,
        val amount: Long,
        val emphasized: Boolean = false,
        val contra: Boolean = false
    ) : BalanceSheetRow()
    data class DateLine(val timestampMs: Long) : BalanceSheetRow()
}
