package dev.fitiavana.accounting.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.alerts.AlertCheckManager
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.exchangerates.RateRefresher
import dev.fitiavana.accounting.features.exchangerates.RefreshResult
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

class HomeDetailViewModel(
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val rateRefresher: RateRefresher,
    private val alertCheckManager: AlertCheckManager
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

    /** Fetches the latest price of [accountId]'s instrument, then checks the alerts. Synchronous — callers must invoke this off the main thread. */
    fun refreshRates(accountId: String): RefreshResult {
        val result = rateRefresher.refresh(accountId)
        // A failing alert check must not hide the refresh result from the screen.
        try {
            alertCheckManager.check()
        } catch (e: Exception) {
            Log.w(TAG, "Gain/loss alert check failed", e)
        }
        return result
    }

    private companion object {
        const val TAG = "HomeDetailViewModel"
    }
}
