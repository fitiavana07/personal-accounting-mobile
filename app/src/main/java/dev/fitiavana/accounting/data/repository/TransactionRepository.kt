package dev.fitiavana.accounting.data.repository

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.data.dao.TransactionDao
import dev.fitiavana.accounting.data.model.Transaction
import dev.fitiavana.accounting.data.model.TransactionEntry
import dev.fitiavana.accounting.data.model.TransactionWithEntries

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
