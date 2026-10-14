package dev.fitiavana.accounting.features.transactions

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind

class TransactionRepository(
    private val dao: TransactionDao,
    private val tracker: ChangeTracker
) {
    fun getWithEntries(id: String): TransactionWithEntries? =
        dao.getWithEntries(id)

    /** Stores a transaction with its entries, all or nothing; they are one synced record. */
    fun save(transaction: Transaction, entries: List<TransactionEntry>) = tracker.inTransaction {
        dao.insert(transaction)
        entries.forEach { dao.insertEntry(it) }
        tracker.touch(SyncKind.TRANSACTION, transaction.id)
    }

    fun getFilteredWithEntries(
        startMs: Long,
        endMs: Long,
        accountId: String?
    ): LiveData<List<TransactionWithEntries>> =
        dao.getFilteredWithEntries(startMs, endMs, accountId)

    fun clearAll() = tracker.inTransaction {
        val ids = dao.getAllIds()
        dao.deleteAllEntries()
        dao.deleteAll()
        tracker.deleted(ids.map { RecordKey(SyncKind.TRANSACTION, it) })
    }
}
