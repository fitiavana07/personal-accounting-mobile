package dev.fitiavana.accounting.features.bankaccounts

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.SyncKind
import java.util.UUID

/**
 * Creates, renames and lists bank accounts and adds transactions to them, keeping each account's
 * balance and balance date up to date. Writes are synchronous: use them off the main thread.
 */
class BankAccountRepository(
    private val dao: BankAccountDao,
    private val tracker: ChangeTracker,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    fun getAccounts(): LiveData<List<BankAccount>> = dao.getAllAccounts()

    fun getAccountLive(id: String): LiveData<BankAccount?> = dao.getAccount(id)

    fun getAccount(id: String): BankAccount? = dao.getAccountSync(id)

    fun getTransactions(accountId: String): LiveData<List<BankAccountTransaction>> = dao.getTransactions(accountId)

    fun createAccount(name: String): BankAccount {
        val account = BankAccount(idGenerator(), requireName(name), 0, null)
        saveAccount(account)
        return account
    }

    fun rename(id: String, name: String) {
        val account = requireNotNull(dao.getAccountSync(id)) { "Unknown bank account $id" }
        saveAccount(account.copy(name = requireName(name)))
    }

    /** [amount] is positive for a credit and negative for a debit; it is never zero. */
    fun addTransaction(accountId: String, amount: Long, note: String, transactionDateTime: Long): BankAccountTransaction {
        require(amount != 0L) { "Amount must not be zero" }
        val transaction = BankAccountTransaction(idGenerator(), accountId, amount, note, clock(), transactionDateTime)
        tracker.inTransaction {
            val account = requireNotNull(dao.getAccountSync(accountId)) { "Unknown bank account $accountId" }
            dao.insertTransaction(transaction)
            dao.insertAccount(
                account.copy(
                    balance = account.balance + amount,
                    balanceDate = maxOf(account.balanceDate ?: transactionDateTime, transactionDateTime)
                )
            )
            // Only the transaction is stamped: the balance is derived from the transactions after every
            // sync merge, so restamping the account would let it win over a rename made on another device.
            tracker.touch(SyncKind.BANK_ACCOUNT_TRANSACTION, transaction.id)
        }
        return transaction
    }

    private fun requireName(name: String) = name.trim().also { require(it.isNotEmpty()) { "Name must not be blank" } }

    private fun saveAccount(account: BankAccount) = tracker.inTransaction {
        dao.insertAccount(account)
        tracker.touch(SyncKind.BANK_ACCOUNT, account.id)
    }
}
