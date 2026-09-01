package dev.fitiavana.accounting.ui.accounts

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.instruments.Instrument

data class AccountListItem(
    val account: Account,
    val balance: Long,
    val instrumentBalance: Long,
    val instrument: Instrument?,
    val intermediaryBalance: Long,
    val intermediaryInstrument: Instrument?,
    val updatedAt: Long?
)

object AccountListItemBuilder {
    fun build(
        accounts: List<Account>,
        balances: List<AccountBalance>,
        instruments: Map<String, Instrument>
    ): List<AccountListItem> {
        val balanceMap = balances.associateBy { it.accountId }
        return accounts.map { account ->
            val balance = balanceMap[account.id]
            AccountListItem(
                account = account,
                balance = balance?.balance ?: 0,
                instrumentBalance = balance?.instrumentBalance ?: 0,
                instrument = account.instrumentCode?.let { instruments[it] },
                intermediaryBalance = balance?.intermediaryBalance ?: 0,
                intermediaryInstrument = account.intermediaryInstrumentCode?.let { instruments[it] },
                updatedAt = balance?.updatedAt
            )
        }
    }
}
