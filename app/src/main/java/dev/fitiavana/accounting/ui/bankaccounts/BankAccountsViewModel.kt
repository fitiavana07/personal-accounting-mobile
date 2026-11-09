package dev.fitiavana.accounting.ui.bankaccounts

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.bankaccounts.BankAccount
import dev.fitiavana.accounting.features.bankaccounts.BankAccountRepository

/** The bank accounts with their balances, and creating a new one. */
class BankAccountsViewModel(private val repository: BankAccountRepository) : ViewModel() {

    val accounts: LiveData<List<BankAccount>> = repository.getAccounts()

    /** Creates an account in the background; [onDone] runs on the main thread with whether it was created. */
    fun create(name: String, onDone: (Boolean) -> Unit) {
        if (name.isBlank()) return onDone(false)
        Thread {
            val created = createSync(name)
            Handler(Looper.getMainLooper()).post { onDone(created) }
        }.start()
    }

    internal fun createSync(name: String): Boolean {
        if (name.isBlank()) return false
        repository.createAccount(name.trim())
        return true
    }
}

class BankAccountsViewModelFactory(private val repository: BankAccountRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BankAccountsViewModel(repository) as T
    }
}
