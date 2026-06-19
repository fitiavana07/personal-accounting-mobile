package dev.fitiavana.accounting.data.repository

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.data.dao.AccountDao
import dev.fitiavana.accounting.data.model.Account

class AccountRepository(private val dao: AccountDao) {
    fun getAll(): LiveData<List<Account>> = dao.getAll()
    fun getById(id: String): Account? = dao.getById(id)
    fun insert(account: Account) = dao.insert(account)
    fun update(account: Account) = dao.update(account)
    fun delete(account: Account) = dao.delete(account)
}
