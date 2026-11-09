package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountTypes

/** The accounts a transaction fee (swap or transfer) can be expensed to, and its entry. */
object FeeAccounts {

    /** Expense accounts (always in base currency). */
    fun selectable(accounts: List<Account>): List<Account> = selectableExcluding(accounts, null)

    /** Expense accounts other than [excludedId], so two fees of one transaction can't share an account. */
    fun selectableExcluding(accounts: List<Account>, excludedId: String?): List<Account> =
        accounts.filter { it.type == AccountTypes.EXPENSE && it.id != excludedId }

    /** The expense entry of a fee: debits [baseAmount] to [expenseAccountId], in base currency only. */
    fun expenseEntry(expenseAccountId: String, baseAmount: Long) = TransactionValidator.EntryData(
        accountId = expenseAccountId,
        debitAmount = baseAmount,
        creditAmount = null
    )
}
