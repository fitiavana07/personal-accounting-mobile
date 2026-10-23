package dev.fitiavana.accounting.features.exchangerates

import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountGainLoss
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

/**
 * Refreshes the cached rates of every account that has a gain/loss on Home.
 * Shared by the Home refresh and the background gain/loss alert check.
 */
class RateRefresher(
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) {
    /**
     * Refreshes every position's rate, or only [accountId]'s when given.
     * Synchronous — callers must invoke this off the main thread.
     */
    fun refresh(accountId: String? = null): RefreshResult {
        val positions = AccountGainLoss.positions(
            balanceRepository.getAllSync(),
            accountRepository.getAllSync(),
            instrumentRepository.getAllSync().associateBy { it.code },
            emptyMap()
        )
        val pairs = positions
            .filter { accountId == null || it.account.id == accountId }
            .map { it.instrument to it.intermediaryInstrument }
            .distinctBy { it.first.code to it.second.code }
        return exchangeRateRepository.refresh(pairs)
    }
}
