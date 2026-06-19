package dev.fitiavana.accounting.ui.transactions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.TransactionWithEntries
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.TransactionRepository

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    val transactions: LiveData<List<TransactionWithEntries>> = transactionRepository.getAllWithEntries()
    val accounts: LiveData<List<Account>> = accountRepository.getAll()

    val combined = MediatorLiveData<Pair<List<TransactionWithEntries>, List<Account>>>().apply {
        var latestTransactions: List<TransactionWithEntries> = emptyList()
        var latestAccounts: List<Account> = emptyList()

        addSource(transactions) { t ->
            latestTransactions = t ?: emptyList()
            value = Pair(latestTransactions, latestAccounts)
        }
        addSource(accounts) { a ->
            latestAccounts = a ?: emptyList()
            value = Pair(latestTransactions, latestAccounts)
        }
    }
}
