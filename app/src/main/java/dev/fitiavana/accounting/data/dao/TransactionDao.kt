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

    @Query("SELECT * FROM transactions")
    fun getAllTransactionsSync(): List<dev.fitiavana.accounting.data.model.Transaction>

    @Query("SELECT * FROM transaction_entries")
    fun getAllEntriesSync(): List<TransactionEntry>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(transaction: dev.fitiavana.accounting.data.model.Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllTransactions(transactions: List<dev.fitiavana.accounting.data.model.Transaction>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEntry(entry: TransactionEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllEntries(entries: List<TransactionEntry>)

    @Query("SELECT COALESCE(SUM(debitAmount), 0) FROM transaction_entries WHERE accountId = :accountId")
    fun sumDebitsForAccount(accountId: String): Long

    @Query("SELECT COALESCE(SUM(creditAmount), 0) FROM transaction_entries WHERE accountId = :accountId")
    fun sumCreditsForAccount(accountId: String): Long

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

    @Query(
        """
        SELECT COALESCE(SUM(te.debitAmount), 0) FROM transaction_entries te
        JOIN transactions t ON t.id = te.transactionId
        WHERE te.accountId = :accountId AND t.transactionDatetime <= :asOfMs
    """
    )
    fun sumDebitsForAccountUpTo(accountId: String, asOfMs: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(te.creditAmount), 0) FROM transaction_entries te
        JOIN transactions t ON t.id = te.transactionId
        WHERE te.accountId = :accountId AND t.transactionDatetime <= :asOfMs
    """
    )
    fun sumCreditsForAccountUpTo(accountId: String, asOfMs: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(te.debitAmount), 0) FROM transaction_entries te
        JOIN transactions t ON t.id = te.transactionId
        WHERE te.accountId = :accountId AND t.transactionDatetime >= :startMs AND t.transactionDatetime <= :endMs
    """
    )
    fun sumDebitsForAccountBetween(accountId: String, startMs: Long, endMs: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(te.creditAmount), 0) FROM transaction_entries te
        JOIN transactions t ON t.id = te.transactionId
        WHERE te.accountId = :accountId AND t.transactionDatetime >= :startMs AND t.transactionDatetime <= :endMs
    """
    )
    fun sumCreditsForAccountBetween(accountId: String, startMs: Long, endMs: Long): Long

    @Query("SELECT MIN(transactionDatetime) FROM transactions")
    fun getMinTransactionDatetime(): Long?

    @Query("SELECT MAX(transactionDatetime) FROM transactions")
    fun getMaxTransactionDatetime(): Long?

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
