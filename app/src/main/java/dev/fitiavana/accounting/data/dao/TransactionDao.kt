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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(transaction: dev.fitiavana.accounting.data.model.Transaction)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEntry(entry: TransactionEntry)

    @Query("SELECT COALESCE(SUM(debitAmount), 0) FROM transaction_entries WHERE accountId = :accountId")
    fun sumDebitsForAccount(accountId: String): Int

    @Query("SELECT COALESCE(SUM(creditAmount), 0) FROM transaction_entries WHERE accountId = :accountId")
    fun sumCreditsForAccount(accountId: String): Int

    @Query("SELECT COALESCE(SUM(instrumentDebitAmount), 0) FROM transaction_entries WHERE accountId = :accountId")
    fun sumInstrumentDebitsForAccount(accountId: String): Long

    @Query("SELECT COALESCE(SUM(instrumentCreditAmount), 0) FROM transaction_entries WHERE accountId = :accountId")
    fun sumInstrumentCreditsForAccount(accountId: String): Long

    @Query("SELECT COALESCE(SUM(intermediaryDebitAmount), 0) FROM transaction_entries WHERE accountId = :accountId")
    fun sumIntermediaryDebitsForAccount(accountId: String): Long

    @Query("SELECT COALESCE(SUM(intermediaryCreditAmount), 0) FROM transaction_entries WHERE accountId = :accountId")
    fun sumIntermediaryCreditsForAccount(accountId: String): Long

    @Query("SELECT COUNT(*) FROM transaction_entries WHERE accountId = :accountId")
    fun countEntriesForAccount(accountId: String): Int

    @Transaction
    @Query(
        """
        SELECT * FROM transactions
        WHERE transactionDatetime >= :startMs AND transactionDatetime <= :endMs
        AND (:accountId IS NULL OR id IN (SELECT transactionId FROM transaction_entries WHERE accountId = :accountId))
        ORDER BY transactionDatetime DESC
    """
    )
    fun getFilteredWithEntries(
        startMs: Long,
        endMs: Long,
        accountId: String?
    ): LiveData<List<TransactionWithEntries>>

    @Query("DELETE FROM transaction_entries")
    fun deleteAllEntries()

    @Query("DELETE FROM transactions")
    fun deleteAll()
}
