package dev.fitiavana.accounting.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

class EditAccountViewModelFactory(
    private val repository: AccountRepository,
    private val instrumentRepository: InstrumentRepository,
    private val balanceRepository: BalanceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return EditAccountViewModel(repository, instrumentRepository, balanceRepository) as T
    }
}