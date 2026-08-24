package dev.fitiavana.accounting.features.transactions

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.transactions.TransactionDao
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.features.transactions.TransactionWithEntries

class TransactionRepository(private val dao: TransactionDao) {
    fun getAllWithEntries(): LiveData<List<TransactionWithEntries>> = dao.getAllWithEntries()
    fun getWithEntries(id: String): TransactionWithEntries? = dao.getWithEntries(id)
    fun insert(transaction: Transaction) = dao.insert(transaction)
    fun insertEntry(entry: TransactionEntry) = dao.insertEntry(entry)
    fun getFilteredWithEntries(startMs: Long, endMs: Long, accountId: String?): LiveData<List<TransactionWithEntries>> =
        dao.getFilteredWithEntries(startMs, endMs, accountId)
    fun clearAll() {
        dao.deleteAllEntries()
        dao.deleteAll()
    }
}
