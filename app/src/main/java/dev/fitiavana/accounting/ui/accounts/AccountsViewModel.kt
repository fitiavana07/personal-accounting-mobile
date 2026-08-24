package dev.fitiavana.accounting.ui.accounts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository

class AccountsViewModel(private val repository: AccountRepository) :
    ViewModel() {

    private val allAccounts: LiveData<List<Account>> = repository.getAll()
    val typeFilter = MutableLiveData<String?>(null)

    val accounts: LiveData<List<Account>> =
        MediatorLiveData<List<Account>>().apply {
            fun update() {
                val list = allAccounts.value ?: emptyList()
                val type = typeFilter.value
                value =
                    if (type == null) list else list.filter { it.type == type }
            }
            addSource(allAccounts) { update() }
            addSource(typeFilter) { update() }
        }

    fun setTypeFilter(type: String?) {
        typeFilter.value = type
    }
}