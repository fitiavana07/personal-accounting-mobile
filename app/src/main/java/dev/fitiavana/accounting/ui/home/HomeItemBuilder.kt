package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.model.ExchangeRateCache
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.features.balances.GainLossCalculator
import dev.fitiavana.accounting.ui.common.TransactionDisplay

data class HomeItem(
    val accountId: String,
    val accountName: String,
    val instrument: Instrument,
    val intermediaryInstrument: Instrument,
    val instrumentBalanceFormatted: String,
    val bookValue: Double,
    val currentValue: Double?,
    val currentValueAr: Double?,
    val gainLoss: Double?,
    val gainLossPercent: Double?,
    val bookValueAr: Long,
    val gainLossAr: Double?,
    val bookRate: String?,
    val currentRate: String?,
    val rateFetchedAt: Long?
)

object HomeItemBuilder {
    private const val MIN_BALANCE_AR = 10_000L

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
            if (balance.balance < MIN_BALANCE_AR) return@mapNotNull null

            val instrument = account.instrumentCode?.let { instruments[it] } ?: return@mapNotNull null
            if (instrument.type != "cryptocurrency" && instrument.type != "stock") return@mapNotNull null

            val intermediaryInstrument = account.intermediaryInstrumentCode?.let { instruments[it] }
                ?: return@mapNotNull null

            val bookValue = balance.intermediaryBalance / Math.pow(10.0, intermediaryInstrument.decimalPlaces.toDouble())

            val cached = rates[ExchangeRateCache.pairKey(instrument.code, intermediaryInstrument.code)]
            val currentValue = cached?.let {
                GainLossCalculator.computeCurrentValue(balance.instrumentBalance, instrument.decimalPlaces, it.rate)
            }
            val gainLoss = currentValue?.let { GainLossCalculator.computeGainLoss(it, bookValue) }
            val gainLossPercent = gainLoss?.let { GainLossCalculator.computeGainLossPercent(it, bookValue) }

            val currentValueAr = if (bookValue == 0.0) null else currentValue?.let { it / bookValue * balance.balance }
            val gainLossAr = currentValueAr?.let { it - balance.balance }

            val instrumentBalanceFormatted = TransactionDisplay.formatInstrumentAmount(balance.instrumentBalance, instrument)

            val bookRate = TransactionDisplay.formatInstrumentExchangeRate(
                balance.instrumentBalance, instrument, balance.intermediaryBalance, intermediaryInstrument
            )
            val currentRate = cached?.let { TransactionDisplay.formatInstrumentRate(instrument, it.rate, intermediaryInstrument) }

            HomeItem(
                accountId = balance.accountId,
                accountName = account.name,
                instrument = instrument,
                intermediaryInstrument = intermediaryInstrument,
                instrumentBalanceFormatted = instrumentBalanceFormatted,
                bookValue = bookValue,
                currentValue = currentValue,
                currentValueAr = currentValueAr,
                gainLoss = gainLoss,
                gainLossPercent = gainLossPercent,
                bookValueAr = balance.balance,
                gainLossAr = gainLossAr,
                bookRate = bookRate,
                currentRate = currentRate,
                rateFetchedAt = cached?.fetchedAt
            )
        }
    }
}
