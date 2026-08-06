package dev.fitiavana.accounting.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.BalanceRepository

class ReportsViewModelFactory(
    private val accountRepository: AccountRepository,
    private val balanceRepository: BalanceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ReportsViewModel(accountRepository, balanceRepository) as T
    }
}
