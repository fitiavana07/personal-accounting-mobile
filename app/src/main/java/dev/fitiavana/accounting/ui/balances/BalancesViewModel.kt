package dev.fitiavana.accounting.ui.balances

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.BalanceRepository

class BalancesViewModel(
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val balances: LiveData<List<AccountBalance>> = balanceRepository.getAll()
    private val accounts: LiveData<List<Account>> = accountRepository.getAll()

    val balanceItems = MediatorLiveData<List<BalanceItem>>().apply {
        var latestBalances: List<AccountBalance> = emptyList()
        var latestAccounts: List<Account> = emptyList()

        fun update() {
            value = BalanceItemBuilder.build(latestBalances, latestAccounts)
        }

        addSource(balances) { b ->
            latestBalances = b ?: emptyList()
            update()
        }
        addSource(accounts) { a ->
            latestAccounts = a ?: emptyList()
            update()
        }
    }

    fun recalculateAll() {
        balanceRepository.recalculateAll()
    }
}
