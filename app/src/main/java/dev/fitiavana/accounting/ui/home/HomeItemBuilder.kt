package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.balances.AccountGainLoss
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
    fun build(
        balances: List<AccountBalance>,
        accounts: List<Account>,
        instruments: Map<String, Instrument>,
        rates: Map<String, ExchangeRateCache>
    ): List<HomeItem> {
        return AccountGainLoss.positions(balances, accounts, instruments, rates).map { position ->
            val balance = position.balance
            val account = position.account
            val instrument = position.instrument
            val intermediaryInstrument = position.intermediaryInstrument
            val bookValue = position.bookValue
            val cached = position.cachedRate
            val currentValue = position.currentValue
            val gainLoss = position.gainLoss
            val gainLossPercent = position.gainLossPercent

            val currentValueAr =
                if (bookValue == 0.0) null else currentValue?.let { it / bookValue * balance.balance }
            val gainLossAr = currentValueAr?.let { it - balance.balance }

            val instrumentBalanceFormatted =
                TransactionDisplay.formatInstrumentAmount(
                    balance.instrumentBalance,
                    instrument
                )

            val bookRate = TransactionDisplay.formatInstrumentExchangeRate(
                balance.instrumentBalance,
                instrument,
                balance.intermediaryBalance,
                intermediaryInstrument
            )
            val currentRate = cached?.let {
                TransactionDisplay.formatInstrumentRate(
                    instrument,
                    it.rate,
                    intermediaryInstrument
                )
            }

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
