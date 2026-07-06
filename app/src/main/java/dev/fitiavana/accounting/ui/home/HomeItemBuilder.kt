package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.model.ExchangeRateCache
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.ui.transactions.TransactionDisplay

data class HomeItem(
    val accountId: String,
    val accountName: String,
    val instrument: Instrument,
    val intermediaryInstrument: Instrument,
    val bookValue: Double,
    val currentValue: Double?,
    val gainLoss: Double?,
    val gainLossPercent: Double?,
    val bookRate: String?,
    val currentRate: String?,
    val rateFetchedAt: Long?
)

object HomeItemBuilder {
    fun build(
        balances: List<AccountBalance>,
        accounts: List<Account>,
        instruments: Map<String, Instrument>,
        rates: Map<String, ExchangeRateCache>
    ): List<HomeItem> {
        val accountMap = accounts.associateBy { it.id }
        return balances.mapNotNull { balance ->
            val account = accountMap[balance.accountId] ?: return@mapNotNull null
            if (account.type != "asset") return@mapNotNull null

            val instrument = account.instrumentCode?.let { instruments[it] } ?: return@mapNotNull null
            if (instrument.type != "cryptocurrency") return@mapNotNull null

            val intermediaryInstrument = account.intermediaryInstrumentCode?.let { instruments[it] }
                ?: return@mapNotNull null

            val bookValue = balance.intermediaryBalance / Math.pow(10.0, intermediaryInstrument.decimalPlaces.toDouble())

            val cached = rates[ExchangeRateCache.pairKey(instrument.code, intermediaryInstrument.code)]
            val currentValue = cached?.let {
                GainLossCalculator.computeCurrentValue(balance.instrumentBalance, instrument.decimalPlaces, it.rate)
            }
            val gainLoss = currentValue?.let { GainLossCalculator.computeGainLoss(it, bookValue) }
            val gainLossPercent = gainLoss?.let { GainLossCalculator.computeGainLossPercent(it, bookValue) }

            val bookRate = TransactionDisplay.formatInstrumentExchangeRate(
                balance.instrumentBalance, instrument, balance.intermediaryBalance, intermediaryInstrument
            )
            val currentRate = cached?.let { TransactionDisplay.formatInstrumentRate(instrument, it.rate, intermediaryInstrument) }

            HomeItem(
                accountId = balance.accountId,
                accountName = account.name,
                instrument = instrument,
                intermediaryInstrument = intermediaryInstrument,
                bookValue = bookValue,
                currentValue = currentValue,
                gainLoss = gainLoss,
                gainLossPercent = gainLossPercent,
                bookRate = bookRate,
                currentRate = currentRate,
                rateFetchedAt = cached?.fetchedAt
            )
        }
    }
}
