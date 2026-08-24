package dev.fitiavana.accounting.ui.common

import dev.fitiavana.accounting.ui.home.AssetPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.fitiavana.accounting.features.reports.ReportRow as RawRow

/** Turns raw [RawRow]s (unformatted amounts, no colors) into display-ready [ReportDisplayRow]s. */
object ReportPresenter {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun present(rows: List<RawRow>): List<ReportDisplayRow> = rows.map { row ->
        when (row) {
            is RawRow.Title -> ReportDisplayRow.Title(row.text)
            is RawRow.SectionHeader -> ReportDisplayRow.SectionHeader(row.title)
            is RawRow.SubsectionHeader -> ReportDisplayRow.SubsectionHeader(row.title)
            is RawRow.AccountLine -> ReportDisplayRow.AccountLine(
                row.name,
                formatAmount(row.amount, arPrefixed = row.arPrefixed, contra = row.contra),
                row.assetIndex?.let { AssetPalette.colorFor(it) }
            )
            is RawRow.TotalLine -> ReportDisplayRow.TotalLine(
                row.label,
                formatAmount(row.amount, arPrefixed = true, contra = row.contra),
                row.emphasized
            )
            is RawRow.DateLine -> ReportDisplayRow.DateLine(
                "Balances at ${dateFormat.format(Date(row.timestampMs))}"
            )
        }
    }

    /**
     * Trailing space on the non-parenthesized branch keeps the final digit aligned with
     * parenthesized amounts (whose closing ")" would otherwise sit one character further right),
     * since amounts are rendered in a monospace font.
     */
    private fun formatAmount(amount: Long, arPrefixed: Boolean, contra: Boolean): String {
        val prefix = if (arPrefixed) "Ar " else ""
        return if (contra) {
            "($prefix${TransactionDisplay.formatAmount(Math.abs(amount))})"
        } else {
            "$prefix${TransactionDisplay.formatAmount(amount)} "
        }
    }
}
