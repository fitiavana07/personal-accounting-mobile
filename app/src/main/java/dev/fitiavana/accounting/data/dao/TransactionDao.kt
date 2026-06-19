package dev.fitiavana.accounting.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.fitiavana.accounting.data.model.TransactionEntry
import dev.fitiavana.accounting.data.model.TransactionWithEntries

@Dao
interface TransactionDao {

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY transactionDatetime DESC")
    fun getAllWithEntries(): LiveData<List<TransactionWithEntries>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getWithEntries(id: String): TransactionWithEntries?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(transaction: dev.fitiavana.accounting.data.model.Transaction)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEntry(entry: TransactionEntry)
}
