package dev.fitiavana.accounting.features.bankaccounts

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface BankAccountDao {

    @Query("SELECT * FROM bank_accounts ORDER BY name COLLATE NOCASE, id")
    fun getAllAccounts(): LiveData<List<BankAccount>>

    @Query("SELECT * FROM bank_accounts ORDER BY name COLLATE NOCASE, id")
    fun getAllAccountsSync(): List<BankAccount>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    fun getAccount(id: String): LiveData<BankAccount?>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    fun getAccountSync(id: String): BankAccount?

    @Query("SELECT * FROM bank_account_transactions WHERE accountId = :accountId ORDER BY transactionDateTime DESC, createdAt DESC, id")
    fun getTransactions(accountId: String): LiveData<List<BankAccountTransaction>>

    @Query("SELECT * FROM bank_account_transactions WHERE accountId = :accountId ORDER BY transactionDateTime DESC, createdAt DESC, id")
    fun getTransactionsSync(accountId: String): List<BankAccountTransaction>

    @Query("SELECT * FROM bank_account_transactions ORDER BY createdAt, id")
    fun getAllTransactionsSync(): List<BankAccountTransaction>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM bank_account_transactions WHERE accountId = :accountId")
    fun sumOfTransactions(accountId: String): Long

    @Query("SELECT MAX(transactionDateTime) FROM bank_account_transactions WHERE accountId = :accountId")
    fun latestTransactionDate(accountId: String): Long?

    /**
     * Inserts or updates in place. Never REPLACE: it deletes the row first, and the foreign key
     * cascade would then delete the account's transactions.
     */
    @Upsert
    fun insertAccount(account: BankAccount)

    @Upsert
    fun insertAccounts(accounts: List<BankAccount>)

    /** Plain insert: bank account transactions are never modified, only added. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTransaction(transaction: BankAccountTransaction)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTransactions(transactions: List<BankAccountTransaction>)

    /** Also removes every transaction (foreign key cascade). */
    @Query("DELETE FROM bank_accounts")
    fun deleteAllAccounts()
}
