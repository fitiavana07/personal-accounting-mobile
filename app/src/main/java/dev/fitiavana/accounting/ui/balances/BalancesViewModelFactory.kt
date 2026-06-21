package dev.fitiavana.accounting.ui.balances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.BalanceRepository
import dev.fitiavana.accounting.data.repository.InstrumentRepository

class BalancesViewModelFactory(
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BalancesViewModel(balanceRepository, accountRepository, instrumentRepository) as T
    }
}
