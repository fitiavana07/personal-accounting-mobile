package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance

/**
 * Shared helpers for building the report line items shown in reports such as
 * the balance sheet and income statement. Given a set of account balances,
 * these functions filter down to a single account type (e.g. "asset",
 * "liability", "income", "expense") and resolve each account ID to a
 * human-readable, sorted list of [NamedAmount] rows ready for display.
 */

/**
 * A single report row: an account's display [name] paired with its [amount] —
 * a balance on the balance sheet, or a period total on the income statement.
 */
internal data class NamedAmount(val name: String, val amount: Long)

/**
 * Builds the [NamedAmount] rows for accounts of the given [type], resolving
 * balances from a list of [AccountBalance] records.
 */
internal fun linesFor(
    accountMap: Map<String, Account>,
    balances: List<AccountBalance>,
    type: String
): List<NamedAmount> = linesFor(
    accountMap,
    balances.associate { it.accountId to it.balance },
    type
)

/**
 * Builds the [NamedAmount] rows for accounts of the given [type].
 *
 * Filters [balancesByAccountId] down to accounts present in [accountMap]
 * whose type matches [type], drops zero balances, and maps the remaining
 * entries to [NamedAmount] rows sorted alphabetically by account name.
 */
internal fun linesFor(
    accountMap: Map<String, Account>,
    balancesByAccountId: Map<String, Long>,
    type: String
): List<NamedAmount> = balancesByAccountId.entries
    .asSequence()
    .filter { accountMap.containsKey(it.key) }
    .filter { accountMap.getValue(it.key).type == type }
    .filter { it.value != 0L }
    .map { NamedAmount(accountMap.getValue(it.key).name, it.value) }
    .sortedBy { it.name }
    .toList()
