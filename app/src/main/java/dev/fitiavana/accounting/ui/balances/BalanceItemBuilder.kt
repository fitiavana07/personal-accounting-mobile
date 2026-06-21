package dev.fitiavana.accounting.ui.balances

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.model.Instrument

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
                BalanceItem(
                    accountId = balance.accountId,
                    accountName = account.name,
                    balance = balance.balance,
                    instrumentBalance = balance.instrumentBalance,
                    instrument = instrument,
                    updatedAt = balance.updatedAt
                )
            }
            .sortedByDescending { it.balance }
    }
}
