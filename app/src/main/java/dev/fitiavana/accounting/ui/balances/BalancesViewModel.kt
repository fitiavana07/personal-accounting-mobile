package dev.fitiavana.accounting.ui.balances

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.BalanceRepository
import dev.fitiavana.accounting.data.repository.InstrumentRepository

class BalancesViewModel(
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository
) : ViewModel() {

    private val balances: LiveData<List<AccountBalance>> = balanceRepository.getAll()
    private val accounts: LiveData<List<Account>> = accountRepository.getAll()
    private val instruments: LiveData<List<Instrument>> = instrumentRepository.getAll()

    val balanceItems = MediatorLiveData<List<BalanceItem>>().apply {
        var latestBalances: List<AccountBalance> = emptyList()
        var latestAccounts: List<Account> = emptyList()
        var latestInstruments: Map<String, Instrument> = emptyMap()

        fun update() {
            value = BalanceItemBuilder.build(latestBalances, latestAccounts, latestInstruments)
        }

        addSource(balances) { b ->
            latestBalances = b ?: emptyList()
            update()
        }
        addSource(accounts) { a ->
            latestAccounts = a ?: emptyList()
            update()
        }
        addSource(instruments) { i ->
            latestInstruments = (i ?: emptyList()).associateBy { it.code }
            update()
        }
    }

    fun recalculateAll() {
        balanceRepository.recalculateAll()
    }
}
