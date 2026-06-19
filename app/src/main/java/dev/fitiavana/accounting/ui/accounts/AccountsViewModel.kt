package dev.fitiavana.accounting.ui.accounts

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.repository.AccountRepository
import java.util.UUID

class AccountsViewModel(private val repository: AccountRepository) : ViewModel() {
    val accounts: LiveData<List<Account>> = repository.getAll()

    fun addAccount(name: String) {
        val account = Account(id = UUID.randomUUID().toString(), name = name.trim())
        Thread { repository.insert(account) }.start()
    }
}
