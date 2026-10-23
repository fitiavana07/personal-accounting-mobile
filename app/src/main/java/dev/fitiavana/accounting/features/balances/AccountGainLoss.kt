package dev.fitiavana.accounting.features.balances

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.instruments.Instrument
import kotlin.math.pow

/**
 * Per-account gain/loss shown on the Home screen. Shared by the Home items
 * and the gain/loss alerts so both always agree.
 */
object AccountGainLoss {
    const val MIN_BALANCE_AR = 10_000L

    class Position(
        val account: Account,
        val balance: AccountBalance,
        val instrument: Instrument,
        val intermediaryInstrument: Instrument,
        val bookValue: Double,
        val cachedRate: ExchangeRateCache?,
        val currentValue: Double?,
        val gainLoss: Double?,
        val gainLossPercent: Double?
    )

    /** Accounts that qualify for a gain/loss, in the order of [balances]. */
    fun positions(
        balances: List<AccountBalance>,
        accounts: List<Account>,
        instruments: Map<String, Instrument>,
        rates: Map<String, ExchangeRateCache>
    ): List<Position> {
        val accountMap = accounts.associateBy { it.id }
        return balances.mapNotNull { balance ->
            val account =
                accountMap[balance.accountId] ?: return@mapNotNull null
            if (account.type != "asset") return@mapNotNull null
            if (balance.balance < MIN_BALANCE_AR) return@mapNotNull null

            val instrument = account.instrumentCode?.let { instruments[it] }
                ?: return@mapNotNull null
            if (instrument.type != "cryptocurrency" && instrument.type != "stock") return@mapNotNull null
            // No CoinGecko id means no rate can ever be fetched for this crypto.
            if (instrument.type == "cryptocurrency" && instrument.coingeckoId == null) return@mapNotNull null

            val intermediaryInstrument =
                account.intermediaryInstrumentCode?.let { instruments[it] }
                    ?: return@mapNotNull null

            val bookValue = balance.intermediaryBalance / 10.0.pow(
                intermediaryInstrument.decimalPlaces.toDouble()
            )

            val cached = rates[ExchangeRateCache.pairKey(
                instrument.code,
                intermediaryInstrument.code
            )]
            val currentValue = cached?.let {
                GainLossCalculator.computeCurrentValue(
                    balance.instrumentBalance,
                    instrument.decimalPlaces,
                    it.rate
                )
            }
            val gainLoss = currentValue?.let {
                GainLossCalculator.computeGainLoss(it, bookValue)
            }
            val gainLossPercent = gainLoss?.let {
                GainLossCalculator.computeGainLossPercent(it, bookValue)
            }

            Position(
                account, balance, instrument, intermediaryInstrument,
                bookValue, cached, currentValue, gainLoss, gainLossPercent
            )
        }
    }

    /** Gain/loss % per qualifying account; null when it cannot be computed. */
    fun percentByAccount(
        balances: List<AccountBalance>,
        accounts: List<Account>,
        instruments: Map<String, Instrument>,
        rates: Map<String, ExchangeRateCache>
    ): Map<String, Double?> =
        positions(balances, accounts, instruments, rates)
            .associate { it.account.id to it.gainLossPercent }
}
