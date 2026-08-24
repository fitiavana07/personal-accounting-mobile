package dev.fitiavana.accounting.ui.balances

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.instruments.Instrument

object BalanceItemBuilder {
    fun build(
        balances: List<AccountBalance>,
        accounts: List<Account>,
        instruments: Map<String, Instrument>
    ): List<BalanceItem> {
        val accountMap = accounts.associateBy { it.id }
        return balances
            .mapNotNull { balance ->
                val account = accountMap[balance.accountId] ?: return@mapNotNull null
                val instrument = account.instrumentCode?.let { instruments[it] }
                val intermediaryInstrument = account.intermediaryInstrumentCode?.let { instruments[it] }
                BalanceItem(
                    accountId = balance.accountId,
                    accountName = account.name,
                    balance = balance.balance,
                    instrumentBalance = balance.instrumentBalance,
                    instrument = instrument,
                    intermediaryBalance = balance.intermediaryBalance,
                    intermediaryInstrument = intermediaryInstrument,
                    updatedAt = balance.updatedAt
                )
            }
            .sortedBy { it.accountName }
    }
}
