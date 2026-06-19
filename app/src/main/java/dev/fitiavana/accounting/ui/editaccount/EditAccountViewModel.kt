package dev.fitiavana.accounting.ui.editaccount

import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.repository.AccountRepository
import java.util.UUID

class EditAccountViewModel(private val repository: AccountRepository) : ViewModel() {

    fun getAccount(id: String): Account? = repository.getById(id)

    fun saveAccount(id: String?, name: String, type: String) {
        val trimmed = name.trim()
        if (id == null) {
            repository.insert(Account(id = UUID.randomUUID().toString(), name = trimmed, type = type))
        } else {
            repository.update(Account(id = id, name = trimmed, type = type))
        }
    }

    fun deleteAccount(account: Account) {
        repository.delete(account)
    }
}
