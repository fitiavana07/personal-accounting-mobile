package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.templates.TemplateEntry
import dev.fitiavana.accounting.features.templates.TransactionTemplate
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry

/**
 * The synced rows of one device. Balances and rate caches are not here: balances are derived from
 * the entries after every merge, and the rate caches are device-local.
 */
data class SyncData(
    val schemaVersion: Int,
    val instruments: List<Instrument>,
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val transactionEntries: List<TransactionEntry>,
    val templates: List<TransactionTemplate>,
    val templateEntries: List<TemplateEntry>,
    val settings: AppSettings?,
) {
    /** One key per record (entries belong to their transaction or template), duplicates kept. */
    fun keyList(): List<RecordKey> =
        instruments.map { RecordKey(SyncKind.INSTRUMENT, it.code) } +
            accounts.map { RecordKey(SyncKind.ACCOUNT, it.id) } +
            transactions.map { RecordKey(SyncKind.TRANSACTION, it.id) } +
            templates.map { RecordKey(SyncKind.TEMPLATE, it.id) } +
            listOfNotNull(settings?.let { RecordKey(SyncKind.SETTINGS, SETTINGS_ID) })

    fun recordKeys(): Set<RecordKey> = keyList().toSet()

    companion object {
        /** The settings are a single row, so a record of its own with a fixed id. */
        val SETTINGS_ID = AppSettings.SINGLETON_ID.toString()
    }
}

/** A device's whole state: its rows, plus the stamp of every record and every tombstone. */
data class SyncSnapshot(val data: SyncData, val meta: Map<RecordKey, Meta>) {
    /**
     * Why this snapshot cannot be used, or null. Only what a merge needs to trust it: every row has a
     * live stamp and every live stamp has a row. Rules across rows (dangling references) are left to
     * the repair, because a device can legitimately hold such a state (an instrument deleted elsewhere).
     */
    fun violation(): String? {
        val keys = data.keyList()
        val rows = keys.toSet()
        if (rows.size != keys.size) return "The same record appears twice"
        rows.firstOrNull { meta[it]?.deleted != false }?.let { return "The ${it.kind} record ${it.id} has no live stamp" }
        meta.entries.firstOrNull { (key, m) -> !m.deleted && key !in rows }
            ?.let { return "The ${it.key.kind} record ${it.key.id} has a stamp but no row" }
        val transactionIds = data.transactions.map { it.id }.toSet()
        if (data.transactionEntries.any { it.transactionId !in transactionIds }) return "An entry has no transaction"
        val templateIds = data.templates.map { it.id }.toSet()
        if (data.templateEntries.any { it.templateId !in templateIds }) return "A template entry has no template"
        return null
    }
}
