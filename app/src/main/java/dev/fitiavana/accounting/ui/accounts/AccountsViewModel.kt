package dev.fitiavana.accounting.ui.accounts

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.repository.AccountRepository

class AccountsViewModel(private val repository: AccountRepository) : ViewModel() {
    val accounts: LiveData<List<Account>> = repository.getAll()
}
