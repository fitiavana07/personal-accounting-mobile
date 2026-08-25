package dev.fitiavana.accounting.ui.home

import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

class HomeDetailViewModel(
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) : ViewModel() {

    /** Synchronous — callers must invoke this off the main thread. */
    fun findItem(accountId: String): HomeItem? {
        val items = HomeItemBuilder.build(
            balanceRepository.getAllSync(),
            accountRepository.getAllSync(),
            instrumentRepository.getAllSync().associateBy { it.code },
            exchangeRateRepository.getAllCachedSync().associateBy { it.pairKey }
        )
        return items.find { it.accountId == accountId }
    }
}
