package dev.fitiavana.accounting.ui.transactions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.TransactionWithEntries
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.TransactionRepository
import java.util.Calendar

data class TransactionFilter(
    val startMs: Long,
    val endMs: Long,
    val accountId: String?
)

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    val accounts: LiveData<List<Account>> = accountRepository.getAll()

    val filter = MutableLiveData<TransactionFilter>(defaultFilter())

    val filteredTransactions: LiveData<List<TransactionWithEntries>> =
        filter.switchMap { f ->
            transactionRepository.getFilteredWithEntries(f.startMs, f.endMs, f.accountId)
        }

    val combined = MediatorLiveData<Pair<List<TransactionWithEntries>, List<Account>>>().apply {
        var latestTransactions: List<TransactionWithEntries> = emptyList()
        var latestAccounts: List<Account> = emptyList()

        addSource(filteredTransactions) { t ->
            latestTransactions = t ?: emptyList()
            value = Pair(latestTransactions, latestAccounts)
        }
        addSource(accounts) { a ->
            latestAccounts = a ?: emptyList()
            value = Pair(latestTransactions, latestAccounts)
        }
    }

    fun setDateFilter(startMs: Long, endMs: Long) {
        val current = filter.value ?: defaultFilter()
        filter.value = current.copy(startMs = startMs, endMs = endMs)
    }

    fun setAccountFilter(accountId: String?) {
        val current = filter.value ?: defaultFilter()
        filter.value = current.copy(accountId = accountId)
    }

    companion object {
        fun defaultFilter(): TransactionFilter {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startMs = cal.timeInMillis

            val endCal = Calendar.getInstance()
            endCal.set(Calendar.HOUR_OF_DAY, 23)
            endCal.set(Calendar.MINUTE, 59)
            endCal.set(Calendar.SECOND, 59)
            endCal.set(Calendar.MILLISECOND, 999)
            val endMs = endCal.timeInMillis

            return TransactionFilter(startMs, endMs, null)
        }
    }
}