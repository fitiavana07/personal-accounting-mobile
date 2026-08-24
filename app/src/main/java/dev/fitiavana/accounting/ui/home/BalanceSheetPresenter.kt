package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.ui.common.TransactionDisplay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.fitiavana.accounting.features.reports.BalanceSheetRow as RawRow

/** Turns raw [RawRow]s (unformatted amounts, no colors) into display-ready [BalanceSheetRow]s. */
object BalanceSheetPresenter {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun present(rows: List<RawRow>): List<BalanceSheetRow> = rows.map { row ->
        when (row) {
            is RawRow.Title -> BalanceSheetRow.Title(row.text)
            is RawRow.SectionHeader -> BalanceSheetRow.SectionHeader(row.title)
            is RawRow.SubsectionHeader -> BalanceSheetRow.SubsectionHeader(row.title)
            is RawRow.AccountLine -> BalanceSheetRow.AccountLine(
                row.name,
                formatAmount(row.amount, arPrefixed = row.arPrefixed, contra = row.contra),
                row.assetIndex?.let { AssetPalette.colorFor(it) }
            )
            is RawRow.TotalLine -> BalanceSheetRow.TotalLine(
                row.label,
                formatAmount(row.amount, arPrefixed = true, contra = row.contra),
                row.emphasized
            )
            is RawRow.DateLine -> BalanceSheetRow.DateLine(
                "Balances at ${dateFormat.format(Date(row.timestampMs))}"
            )
        }
    }

    private fun formatAmount(amount: Long, arPrefixed: Boolean, contra: Boolean): String {
        val prefix = if (arPrefixed) "Ar " else ""
        return if (contra) {
            "($prefix${TransactionDisplay.formatAmount(Math.abs(amount))})"
        } else {
            "$prefix${TransactionDisplay.formatAmount(amount)}"
        }
    }
}
