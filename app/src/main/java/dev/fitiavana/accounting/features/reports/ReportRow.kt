package dev.fitiavana.accounting.features.reports

/**
 * Raw report row: amounts are unformatted [Long]s and `contra`/`arPrefixed`
 * are display-convention hints (parens for contra amounts, "Ar" prefix), not
 * formatted text. A ui/ presentation step turns these into display strings and
 * colors. Shared by [BalanceSheetBuilder] and [IncomeStatementBuilder].
 */
sealed class ReportRow {
    data class Title(val text: String) : ReportRow()

    data class SectionHeader(val title: String) : ReportRow()

    data class SubsectionHeader(
        val title: String,
        // Same purpose as AccountLine.assetIndex: assigns a color dot from
        // AssetPalette. Only set for the assets-only home view's liquidity
        // level group headers; null elsewhere (e.g. BalanceSheetBuilder.buildMonthly).
        val assetIndex: Int? = null
    ) : ReportRow()

    data class AccountLine(
        val name: String,
        val amount: Long,
        val contra: Boolean = false,
        val arPrefixed: Boolean = false,
        // Position of this line among the assets-only home view's account
        // lines, used to assign it a distinct color from AssetPalette. Null
        // when the line isn't part of that view (e.g. lines from
        // BalanceSheetBuilder.buildMonthly), so no color is assigned.
        val assetIndex: Int? = null
    ) : ReportRow()

    data class TotalLine(
        val label: String,
        val amount: Long,
        // True for grand totals (Total Assets, Total Liabilities, Total
        // Equity, Net Income, ...); false for intermediate subtotals (e.g.
        // Total Original Equity, Total Drawing), which render without the
        // bold/highlighted grand-total styling.
        val emphasized: Boolean = false,
        val contra: Boolean = false,
        // For a total that can be either positive or negative (e.g. a net
        // total combining income/expense/gain/loss), parenthesize it only
        // when it's actually negative — unlike [contra], which always
        // parenthesizes regardless of sign.
        val parenthesizeNegative: Boolean = false
    ) : ReportRow()

    data class DateLine(val timestampMs: Long) : ReportRow()
}
