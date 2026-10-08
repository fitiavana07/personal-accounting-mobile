package dev.fitiavana.accounting.ui.common

import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.reports.NativeAmount
import dev.fitiavana.accounting.ui.home.AssetPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.fitiavana.accounting.features.reports.ReportRow as RawRow

/** Turns raw [RawRow]s (unformatted amounts, no colors) into display-ready [ReportDisplayRow]s. */
object ReportPresenter {

    private val dateFormat =
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun present(
        rows: List<RawRow>,
        instruments: Map<String, Instrument> = emptyMap(),
        expandedAccountIds: Set<String> = emptySet()
    ): List<ReportDisplayRow> = rows.flatMap { row ->
        when (row) {
            is RawRow.Title -> listOf(ReportDisplayRow.Title(row.text))
            is RawRow.SectionHeader -> listOf(ReportDisplayRow.SectionHeader(row.title))
            is RawRow.SubsectionHeader -> listOf(
                ReportDisplayRow.SubsectionHeader(
                    row.title,
                    row.assetIndex?.let { AssetPalette.colorFor(it) }
                )
            )
            is RawRow.AccountLine -> presentAccountLine(row, instruments, expandedAccountIds)

            is RawRow.TotalLine -> listOf(
                ReportDisplayRow.TotalLine(
                    row.label,
                    formatAmount(
                        row.amount,
                        arPrefixed = true,
                        contra = row.contra || (row.parenthesizeNegative && row.amount < 0)
                    ),
                    row.emphasized
                )
            )

            is RawRow.DateLine -> listOf(
                ReportDisplayRow.DateLine(
                    "Balances at ${dateFormat.format(Date(row.timestampMs))}"
                )
            )
        }
    }

    /**
     * The account line, plus its sub-rows when [expandedAccountIds] contains it: the native amounts, then the APR.
     * A line is only expandable when it has at least one sub-row: a native amount with a known instrument
     * (unknown ones are skipped) or an APR.
     */
    private fun presentAccountLine(
        row: RawRow.AccountLine,
        instruments: Map<String, Instrument>,
        expandedAccountIds: Set<String>
    ): List<ReportDisplayRow> {
        val nativeLines = row.nativeAmounts.mapNotNull { native ->
            instruments[native.instrumentCode]?.let { instrument ->
                ReportDisplayRow.NativeLine(
                    native.instrumentCode,
                    "${TransactionDisplay.formatInstrumentValue(native.amount, instrument)} "
                )
            }
        }
        // Same trailing space as the native amounts so the values stay right-aligned together.
        val aprLines = listOfNotNull(
            row.aprPercent?.let { ReportDisplayRow.AprLine("${TransactionDisplay.formatAprPercent(it)} ") }
        )
        val subRows = nativeLines + aprLines
        val expandable = row.accountId != null && subRows.isNotEmpty()
        val expanded = expandable && row.accountId in expandedAccountIds
        val line = ReportDisplayRow.AccountLine(
            row.name,
            formatAmount(row.amount, arPrefixed = row.arPrefixed, contra = row.contra),
            row.assetIndex?.let { AssetPalette.colorFor(it) },
            accountId = row.accountId,
            expandable = expandable,
            expanded = expanded
        )
        return if (expanded) listOf(line) + subRows else listOf(line)
    }

    /**
     * Trailing space on the non-parenthesized branch keeps the final digit aligned with
     * parenthesized amounts (whose closing ")" would otherwise sit one character further right),
     * since amounts are rendered in a monospace font.
     */
    private fun formatAmount(
        amount: Long,
        arPrefixed: Boolean,
        contra: Boolean
    ): String {
        val prefix = if (arPrefixed) "Ar " else ""
        return if (contra) {
            "($prefix${TransactionDisplay.formatAmount(Math.abs(amount))})"
        } else {
            "$prefix${TransactionDisplay.formatAmount(amount)} "
        }
    }
}
