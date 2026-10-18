package dev.fitiavana.accounting.features.accounts

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.SyncKind

class AccountRepository(val dao: AccountDao, private val tracker: ChangeTracker) {
    fun getAll(): LiveData<List<Account>> = dao.getAll()
    fun getAllSync(): List<Account> = dao.getAllSync()
    fun getById(id: String): Account? = dao.getById(id)

    /** True when an asset account has [revenueAccountId] as its pre-associated revenue account. */
    fun hasAccountsLinkedTo(revenueAccountId: String): Boolean = dao.hasAccountsLinkedTo(revenueAccountId)

    fun insert(account: Account) = tracker.inTransaction {
        dao.insert(account)
        tracker.touch(SyncKind.ACCOUNT, account.id)
    }

    fun update(account: Account) = tracker.inTransaction {
        dao.update(account)
        tracker.touch(SyncKind.ACCOUNT, account.id)
    }

    fun delete(account: Account) = tracker.inTransaction {
        tracker.deletedAccount(account.id)
        dao.delete(account)
    }
}
