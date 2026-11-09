package dev.fitiavana.accounting.ui.bankaccounts

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.bankaccounts.BankAccount
import dev.fitiavana.accounting.features.bankaccounts.BankAccountRepository
import dev.fitiavana.accounting.features.bankaccounts.BankAccountTransaction

/** A transaction with the account balance right after it. */
data class BankAccountTransactionRow(val transaction: BankAccountTransaction, val balanceAfter: Long)

/** [newestFirst] transactions paired with the running balance after each one (the sum of it and all older ones). */
internal fun withRunningBalances(newestFirst: List<BankAccountTransaction>): List<BankAccountTransactionRow> {
    var balance = newestFirst.sumOf { it.amount }
    return newestFirst.map { tx ->
        BankAccountTransactionRow(tx, balance).also { balance -= tx.amount }
    }
}

/** One bank account (name, balance, balance date), its transactions, and renaming it. */
class BankAccountDetailViewModel(
    private val repository: BankAccountRepository,
    private val accountId: String
) : ViewModel() {

    val account: LiveData<BankAccount?> = repository.getAccountLive(accountId)

    val transactions: LiveData<List<BankAccountTransactionRow>> =
        MediatorLiveData<List<BankAccountTransactionRow>>().apply {
            addSource(repository.getTransactions(accountId)) { value = withRunningBalances(it) }
        }

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
