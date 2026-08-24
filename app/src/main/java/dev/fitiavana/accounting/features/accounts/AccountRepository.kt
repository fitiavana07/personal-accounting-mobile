package dev.fitiavana.accounting.features.accounts

import androidx.lifecycle.LiveData

class AccountRepository(val dao: AccountDao) {
    fun getAll(): LiveData<List<Account>> = dao.getAll()
    fun getAllSync(): List<Account> = dao.getAllSync()
    fun getById(id: String): Account? = dao.getById(id)
    fun insert(account: Account) = dao.insert(account)
    fun update(account: Account) = dao.update(account)
    fun delete(account: Account) = dao.delete(account)
}
