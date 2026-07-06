package dev.fitiavana.accounting.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.BalanceRepository
import dev.fitiavana.accounting.data.repository.ExchangeRateRepository
import dev.fitiavana.accounting.data.repository.InstrumentRepository

class HomeViewModelFactory(
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(balanceRepository, accountRepository, instrumentRepository, exchangeRateRepository) as T
    }
}
