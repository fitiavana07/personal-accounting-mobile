package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.accounts.AccountDao
import dev.fitiavana.accounting.features.instruments.InstrumentDao
import dev.fitiavana.accounting.features.settings.AppSettingsDao
import dev.fitiavana.accounting.features.templates.TemplateDao
import dev.fitiavana.accounting.features.transactions.TransactionDao

sealed interface ApplyResult {
    /** The peer's state was merged in; [changed] tells whether anything here is different now. */
    data class Applied(val changed: Boolean) : ApplyResult

    /** The peer's state was not used and nothing here changed. */
    data class Rejected(val message: String) : ApplyResult
}

/**
 * Reads this device's state as a [SyncSnapshot] and merges a peer's snapshot into it. The merge is
 * last-write-wins per record, then repaired ([SyncRepair]), checked against [SyncRules] and written
 * in one transaction, so a bad snapshot or a failure halfway leaves the data untouched.
 *
 * Balances are derived from the entries, so they are not synced: [recalculateBalances] rebuilds
 * them inside the same transaction whenever rows changed. Calls are synchronous: use them off the
 * main thread.
 */
class SyncRepository(
    private val tracker: ChangeTracker,
    private val metaDao: SyncMetaDao,
    private val syncDao: SyncDao,
    private val instrumentDao: InstrumentDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val templateDao: TemplateDao,
    private val appSettingsDao: AppSettingsDao,
    private val schemaVersion: Int,
    private val recalculateBalances: () -> Unit,
) {
    fun snapshot(): SyncSnapshot = tracker.inTransaction { read() }

    fun apply(remote: SyncSnapshot): ApplyResult {
        if (remote.data.schemaVersion != schemaVersion) {
            return ApplyResult.Rejected(
                "The other device uses database version ${remote.data.schemaVersion}, this one $schemaVersion",
            )
        }
        remote.violation()?.let { return ApplyResult.Rejected(it) }
        return try {
            tracker.inTransaction { merge(remote) }
        } catch (e: Exception) {
            ApplyResult.Rejected("The data could not be merged: ${e.message ?: "unknown error"}")
        }
    }

    private fun read(): SyncSnapshot {
        val data = SyncData(
            schemaVersion = schemaVersion,
            instruments = instrumentDao.getAllSync(),
            accounts = accountDao.getAllSync(),
            transactions = transactionDao.getAllTransactionsSync(),
            transactionEntries = transactionDao.getAllEntriesSync(),
            templates = templateDao.getAllTemplatesSync(),
            templateEntries = templateDao.getAllEntriesSync(),
            settings = appSettingsDao.getSync(),
        )
        val rows = data.recordKeys()
        val stored = metaDao.all().map { it.key() to it.meta() }.filter { (key, meta) -> meta.deleted || key in rows }
        // A row that never got a stamp (data from before sync existed) is older than any real write.
        val unstamped = (rows - stored.map { it.first }.toSet()).associateWith { Meta(Stamp(0, ""), deleted = false) }
        return SyncSnapshot(data, stored.toMap() + unstamped)
    }

    private fun merge(remote: SyncSnapshot): ApplyResult {
        val local = read()
        val merge = SyncMerge.merge(local.meta, remote.meta)
        tracker.observe(SyncMerge.highestTime(remote.meta))

        val repaired = SyncRepair.repair(mergedRows(local.data, remote.data, merge))
        SyncRules.violation(repaired.data)?.let { error(it) }

        val stored = metaDao.all().associate { it.key() to it.meta() }
        val metaToStore = merge.meta.filter { (key, meta) -> stored[key] != meta }
        metaDao.upsert(metaToStore.map { (key, meta) -> SyncMetaRow.of(key, meta) })
        // What the repair changed is a local write that the peers have not seen: stamp it fresh.
        repaired.modified.groupBy({ it.kind }, { it.id }).forEach { (kind, ids) -> tracker.touch(kind, ids) }
        tracker.deleted(repaired.removed)

        val rowsChanged = write(local.data, repaired.data)
        if (rowsChanged) recalculateBalances()
        return ApplyResult.Applied(
            changed = rowsChanged || metaToStore.isNotEmpty() || repaired.modified.isNotEmpty() || repaired.removed.isNotEmpty(),
        )
    }

    /** This device's rows with the records the peer won taken from the peer, and the ones it deleted dropped. */
    private fun mergedRows(local: SyncData, remote: SyncData, merge: MergeResult) = local.copy(
        instruments = merged(SyncKind.INSTRUMENT, { it.code }, local.instruments, remote.instruments, merge),
        accounts = merged(SyncKind.ACCOUNT, { it.id }, local.accounts, remote.accounts, merge),
        transactions = merged(SyncKind.TRANSACTION, { it.id }, local.transactions, remote.transactions, merge),
        transactionEntries = merged(SyncKind.TRANSACTION, { it.transactionId }, local.transactionEntries, remote.transactionEntries, merge),
        templates = merged(SyncKind.TEMPLATE, { it.id }, local.templates, remote.templates, merge),
        templateEntries = merged(SyncKind.TEMPLATE, { it.templateId }, local.templateEntries, remote.templateEntries, merge),
        settings = mergedSettings(local, remote, merge),
    )

    /** [recordId] gives the record a row belongs to: its own id, or its parent's for entries. */
    private fun <T> merged(
        kind: SyncKind,
        recordId: (T) -> String,
        local: List<T>,
        remote: List<T>,
        merge: MergeResult,
    ): List<T> {
        val taken = merge.takenFromRemote.filter { it.kind == kind }.map { it.id }.toSet()
        val takenAlive = taken.filter { !merge.meta.getValue(RecordKey(kind, it)).deleted }.toSet()
        return local.filter { recordId(it) !in taken } + remote.filter { recordId(it) in takenAlive }
    }

    private fun mergedSettings(local: SyncData, remote: SyncData, merge: MergeResult) =
        RecordKey(SyncKind.SETTINGS, SyncData.SETTINGS_ID).let { key ->
            if (key in merge.takenFromRemote) remote.settings.takeUnless { merge.meta.getValue(key).deleted } else local.settings
        }

    /**
     * Writes only the rows that differ. Children go before their parents on delete and parents first on
     * insert, so the foreign keys hold at every statement; accounts are updated before instruments are
     * deleted, because the repair may have cleared their reference.
     */
    private fun write(before: SyncData, after: SyncData): Boolean {
        val removedTransactionEntries = removed(before.transactionEntries, after.transactionEntries) { it.id }
        val removedTransactions = removed(before.transactions, after.transactions) { it.id }
        val removedTemplateEntries = removed(before.templateEntries, after.templateEntries) { it.id }
        val removedTemplates = removed(before.templates, after.templates) { it.id }
        val removedAccounts = removed(before.accounts, after.accounts) { it.id }
        val removedInstruments = removed(before.instruments, after.instruments) { it.code }
        val instruments = changed(before.instruments, after.instruments)
        val accounts = changed(before.accounts, after.accounts)
        val transactions = changed(before.transactions, after.transactions)
        val transactionEntries = changed(before.transactionEntries, after.transactionEntries)
        val templates = changed(before.templates, after.templates)
        val templateEntries = changed(before.templateEntries, after.templateEntries)

        removedTransactionEntries.inChunks(syncDao::deleteTransactionEntries)
        removedTransactions.inChunks(syncDao::deleteTransactions)
        removedTemplateEntries.inChunks(syncDao::deleteTemplateEntries)
        removedTemplates.inChunks(syncDao::deleteTemplates)
        syncDao.upsertInstruments(instruments)
        syncDao.upsertAccounts(accounts)
        removedAccounts.inChunks(syncDao::deleteAccounts)
        removedInstruments.inChunks(syncDao::deleteInstruments)
        syncDao.upsertTransactions(transactions)
        syncDao.upsertTransactionEntries(transactionEntries)
        syncDao.upsertTemplates(templates)
        syncDao.upsertTemplateEntries(templateEntries)

        val settingsChanged = before.settings != after.settings
        if (settingsChanged) after.settings?.let(syncDao::upsertSettings) ?: syncDao.deleteSettings()

        return settingsChanged || listOf(
            removedTransactionEntries, removedTransactions, removedTemplateEntries, removedTemplates, removedAccounts, removedInstruments,
        ).any { it.isNotEmpty() } || listOf(
            instruments, accounts, transactions, transactionEntries, templates, templateEntries,
        ).any { it.isNotEmpty() }
    }

    private fun <T> changed(before: List<T>, after: List<T>): List<T> {
        val unchanged = before.toSet()
        return after.filter { it !in unchanged }
    }

    private fun <T> removed(before: List<T>, after: List<T>, id: (T) -> String): List<String> =
        (before.map(id).toSet() - after.map(id).toSet()).toList()

    /** SQLite on older Android versions allows few variables per statement. */
    private fun List<String>.inChunks(delete: (List<String>) -> Unit) = chunked(CHUNK).forEach(delete)

    private companion object {
        const val CHUNK = 500
    }
}
