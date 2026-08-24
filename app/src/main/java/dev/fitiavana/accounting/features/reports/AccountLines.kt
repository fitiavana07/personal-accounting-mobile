package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance

internal data class NamedBalance(val name: String, val balance: Long)

internal fun linesFor(
    accountMap: Map<String, Account>,
    balances: List<AccountBalance>,
    type: String
): List<NamedBalance> = linesFor(accountMap, balances.associate { it.accountId to it.balance }, type)

internal fun linesFor(
    accountMap: Map<String, Account>,
    balancesByAccountId: Map<String, Long>,
    type: String
): List<NamedBalance> = balancesByAccountId.entries
    .filter { accountMap.containsKey(it.key) }
    .filter { accountMap.getValue(it.key).type == type }
    .filter { it.value != 0L }
    .map { NamedBalance(accountMap.getValue(it.key).name, it.value) }
    .sortedBy { it.name }
