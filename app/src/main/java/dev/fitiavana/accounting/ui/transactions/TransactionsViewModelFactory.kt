package dev.fitiavana.accounting.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.transactions.TransactionRepository

class TransactionsViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TransactionsViewModel(transactionRepository, accountRepository) as T
    }
}
