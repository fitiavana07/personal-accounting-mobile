package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountTypes

/** The accounts a transaction fee (swap or transfer) can be expensed to. */
object FeeAccounts {

    /** Expense accounts (always in base currency). */
    fun selectable(accounts: List<Account>): List<Account> =
        accounts.filter { it.type == AccountTypes.EXPENSE }
}
