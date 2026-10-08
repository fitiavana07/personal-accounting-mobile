package dev.fitiavana.accounting.ui.earn

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

/** Earn accounts (asset accounts with an APR) and their projected interest, kept current as the data changes. */
class EarnViewModel(
    accountRepository: AccountRepository,
    balanceRepository: BalanceRepository,
    instrumentRepository: InstrumentRepository
) : ViewModel() {

    val state: LiveData<EarnState> = MediatorLiveData<EarnState>().apply {
        var latestAccounts: List<Account> = emptyList()
        var latestBalances: List<AccountBalance> = emptyList()
        var latestInstruments: Map<String, Instrument> = emptyMap()

        fun update() {
            value = EarnItemBuilder.build(latestAccounts, latestBalances, latestInstruments)
        }
        addSource(accountRepository.getAll()) {
            latestAccounts = it ?: emptyList()
            update()
        }
        addSource(balanceRepository.getAll()) {
            latestBalances = it ?: emptyList()
            update()
        }
        addSource(instrumentRepository.getAll()) {
            latestInstruments = (it ?: emptyList()).associateBy { instrument -> instrument.code }
            update()
        }
    }
}

class EarnViewModelFactory(
    private val accountRepository: AccountRepository,
    private val balanceRepository: BalanceRepository,
    private val instrumentRepository: InstrumentRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return EarnViewModel(accountRepository, balanceRepository, instrumentRepository) as T
    }
}
