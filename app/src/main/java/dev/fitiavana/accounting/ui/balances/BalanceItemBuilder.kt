package dev.fitiavana.accounting.ui.balances

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance

object BalanceItemBuilder {
    fun build(balances: List<AccountBalance>, accounts: List<Account>): List<BalanceItem> {
        val accountMap = accounts.associateBy { it.id }
        return balances
            .mapNotNull { balance ->
                val account = accountMap[balance.accountId] ?: return@mapNotNull null
                BalanceItem(
                    accountId = balance.accountId,
                    accountName = account.name,
                    balance = balance.balance,
                    updatedAt = balance.updatedAt
                )
            }
            .sortedByDescending { it.balance }
    }
}
