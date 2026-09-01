package dev.fitiavana.accounting.ui.accounts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

class AccountsViewModel(
    accountRepository: AccountRepository,
    balanceRepository: BalanceRepository,
    instrumentRepository: InstrumentRepository
) : ViewModel() {

    private val allAccounts: LiveData<List<Account>> = accountRepository.getAll()
    private val balances: LiveData<List<AccountBalance>> = balanceRepository.getAll()
    private val instruments: LiveData<List<Instrument>> = instrumentRepository.getAll()
    val typeFilter = MutableLiveData<String?>(null)
    val hideZeroBalance = MutableLiveData(true)

    val accounts: LiveData<List<AccountListItem>> =
        MediatorLiveData<List<AccountListItem>>().apply {
            var latestAccounts: List<Account> = emptyList()
            var latestBalances: List<AccountBalance> = emptyList()
            var latestInstruments: Map<String, Instrument> = emptyMap()

            fun update() {
                val type = typeFilter.value
                val filteredAccounts =
                    if (type == null) latestAccounts else latestAccounts.filter { it.type == type }
                val items = AccountListItemBuilder.build(
                    filteredAccounts,
                    latestBalances,
                    latestInstruments
                )
                value =
                    if (hideZeroBalance.value == true) items.filter { it.balance != 0L } else items
            }
            addSource(allAccounts) { a ->
                latestAccounts = a ?: emptyList()
                update()
            }
            addSource(balances) { b ->
                latestBalances = b ?: emptyList()
                update()
            }
            addSource(instruments) { i ->
                latestInstruments = (i ?: emptyList()).associateBy { it.code }
                update()
            }
            addSource(typeFilter) { update() }
            addSource(hideZeroBalance) { update() }
        }

    fun setTypeFilter(type: String?) {
        typeFilter.value = type
    }

    fun setHideZeroBalance(hide: Boolean) {
        hideZeroBalance.value = hide
    }
}
