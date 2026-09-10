package dev.fitiavana.accounting.ui.transactions

/**
 * Ordering applied to the entries a transaction is saved with, so the stored
 * order reads debits before credits regardless of the entry mode or the order
 * the rows happened to be filled in.
 */
object TransactionEntryOrder {

    /**
     * [entries] with the debit entries first and the rest after, each group
     * keeping its original relative order. An entry counts as a debit when it
     * carries a base-currency debit amount; entries with no amount at all —
     * which [TransactionValidator.validate] rejects before saving — follow the
     * debits.
     */
    fun debitsFirst(
        entries: List<TransactionValidator.EntryData>
    ): List<TransactionValidator.EntryData> {
        val (debits, others) = entries.partition { it.debitAmount != null }
        return debits + others
    }
}
