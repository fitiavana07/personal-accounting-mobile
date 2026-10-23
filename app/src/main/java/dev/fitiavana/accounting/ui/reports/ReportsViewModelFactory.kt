package dev.fitiavana.accounting.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.reports.InMemoryTrendVisibilityStore
import dev.fitiavana.accounting.features.reports.TrendVisibilityStore

class ReportsViewModelFactory(
    private val accountRepository: AccountRepository,
    private val balanceRepository: BalanceRepository,
    private val instrumentRepository: InstrumentRepository,
    private val trendVisibilityStore: TrendVisibilityStore = InMemoryTrendVisibilityStore()
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ReportsViewModel(accountRepository, balanceRepository, instrumentRepository, trendVisibilityStore) as T
    }
}
