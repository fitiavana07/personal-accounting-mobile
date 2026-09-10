package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.features.transactions.TransactionWithEntries
import dev.fitiavana.accounting.ui.common.TransactionDisplay

/** Turns a raw [TransactionWithEntries] into a display-ready [TransactionDetailView]. */
object TransactionDetailPresenter {

    fun present(
        twe: TransactionWithEntries,
        accountsById: Map<String, Account>,
        instrumentsByCode: Map<String, Instrument>
    ): TransactionDetailView {
        val rows = mutableListOf<TransactionDetailRow>()
        var totalDebit = 0L
        var totalCredit = 0L

        for (entry in twe.entries) {
            val account = accountsById[entry.accountId]
            rows.add(
                TransactionDetailRow.Entry(
                    accountName = account?.name ?: entry.accountId,
                    debitText = entry.debitAmount?.let { TransactionDisplay.formatAmount(it) } ?: "",
                    creditText = entry.creditAmount?.let { TransactionDisplay.formatAmount(it) } ?: ""
                )
            )
            totalDebit += entry.debitAmount ?: 0L
            totalCredit += entry.creditAmount ?: 0L

            rows.addAll(subEntryRows(entry, account, instrumentsByCode))
        }

        rows.add(TransactionDetailRow.Total(totalDebit, totalCredit))

        return TransactionDetailView(
            totalAmount = totalDebit,
            note = twe.transaction.note,
            rows = rows
        )
    }

    private fun subEntryRows(
        entry: TransactionEntry,
        account: Account?,
        instrumentsByCode: Map<String, Instrument>
    ): List<TransactionDetailRow.SubEntry> {
        val rows = mutableListOf<TransactionDetailRow.SubEntry>()

        val instrument = account?.instrumentCode?.let { instrumentsByCode[it] }
        if (instrument != null &&
            (entry.instrumentDebitAmount != null || entry.instrumentCreditAmount != null)
        ) {
            rows.add(
                TransactionDetailRow.SubEntry(
                    label = instrument.code,
                    debitText = entry.instrumentDebitAmount?.let {
                        TransactionDisplay.formatInstrumentAmount(it, instrument)
                    } ?: "",
                    creditText = entry.instrumentCreditAmount?.let {
                        TransactionDisplay.formatInstrumentAmount(it, instrument)
                    } ?: ""
                )
            )
        }

        val intermediaryInstrument = account?.intermediaryInstrumentCode?.let { instrumentsByCode[it] }
        if (intermediaryInstrument != null &&
            (entry.intermediaryDebitAmount != null || entry.intermediaryCreditAmount != null)
        ) {
            rows.add(
                TransactionDetailRow.SubEntry(
                    label = intermediaryInstrument.code,
                    debitText = entry.intermediaryDebitAmount?.let {
                        TransactionDisplay.formatInstrumentAmount(it, intermediaryInstrument)
                    } ?: "",
                    creditText = entry.intermediaryCreditAmount?.let {
                        TransactionDisplay.formatInstrumentAmount(it, intermediaryInstrument)
                    } ?: ""
                )
            )
        }

        return rows
    }
}