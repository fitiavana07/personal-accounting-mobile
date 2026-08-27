package dev.fitiavana.accounting.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.settings.AppSettingsRepository

class HomeViewModelFactory(
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val settingsRepository: AppSettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(
            balanceRepository,
            accountRepository,
            instrumentRepository,
            exchangeRateRepository,
            settingsRepository
        ) as T
    }
}
