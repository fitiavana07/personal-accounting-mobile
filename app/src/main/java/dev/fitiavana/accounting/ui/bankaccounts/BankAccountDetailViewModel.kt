package dev.fitiavana.accounting.ui.bankaccounts

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.bankaccounts.BankAccount
import dev.fitiavana.accounting.features.bankaccounts.BankAccountRepository
import dev.fitiavana.accounting.features.bankaccounts.BankAccountTransaction

/** One bank account (name, balance, balance date), its transactions, and renaming it. */
class BankAccountDetailViewModel(
    private val repository: BankAccountRepository,
    private val accountId: String
) : ViewModel() {

    val account: LiveData<BankAccount?> = repository.getAccountLive(accountId)

    val transactions: LiveData<List<BankAccountTransaction>> = repository.getTransactions(accountId)

    /** Renames in the background; [onDone] runs on the main thread with whether the name was accepted. */
    fun rename(name: String, onDone: (Boolean) -> Unit) {
        if (name.isBlank()) return onDone(false)
        Thread {
            val renamed = renameSync(name)
            Handler(Looper.getMainLooper()).post { onDone(renamed) }
        }.start()
    }

    internal fun renameSync(name: String): Boolean {
        if (name.isBlank()) return false
        repository.rename(accountId, name.trim())
        return true
    }
}

class BankAccountDetailViewModelFactory(
    private val repository: BankAccountRepository,
    private val accountId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BankAccountDetailViewModel(repository, accountId) as T
    }
}
