package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountTypes

/**
 * Pure logic behind the "Instrument Transfer" transaction mode: an amount in
 * an account's own instrument moved between two asset accounts that share
 * that instrument (and have no intermediary instrument). The base-currency
 * side of each entry is derived from the From account's own prior
 * balance/instrument-balance ratio, so it carries the From account's cost
 * basis into the To account. The resulting entries are ordinary
 * [TransactionValidator.EntryData] pairs, so balance summary, validation and
 * saving stay shared with the "Classic" mode.
 */
object InstrumentTransferBuilder {

    /**
     * The accounts offered in the From spinner: asset accounts with an
     * instrument and no intermediary instrument. Input order is preserved.
     */
    fun selectableFromAccounts(accounts: List<Account>): List<Account> =
        accounts.filter {
            it.type == AccountTypes.ASSET && it.instrumentCode != null && it.intermediaryInstrumentCode == null
        }

    /**
     * The accounts offered in the To spinner: the same eligible accounts as
     * [selectableFromAccounts], further restricted to [fromAccount]'s
     * instrument. Empty when no From account is selected yet.
     */
    fun selectableToAccounts(accounts: List<Account>, fromAccount: Account?): List<Account> {
        val instrumentCode = fromAccount?.instrumentCode ?: return emptyList()
        return selectableFromAccounts(accounts).filter { it.instrumentCode == instrumentCode }
    }

    /**
     * The two entries an instrument transfer produces: [fromAccountId]
     * credited and [toAccountId] debited by [instrumentAmount] instrument
     * units and [baseAmount] base-currency units. A null or non-positive
     * [instrumentAmount]/[baseAmount] yields null amounts, which
     * [TransactionValidator.validate] reports as incomplete.
     */
    fun buildEntries(
        fromAccountId: String,
        toAccountId: String,
        instrumentAmount: Long?,
        baseAmount: Long?
    ): List<TransactionValidator.EntryData> {
        val instrumentValue = instrumentAmount?.takeIf { it > 0L }
        val baseValue = baseAmount?.takeIf { it > 0L }
        return listOf(
            TransactionValidator.EntryData(
                accountId = fromAccountId,
                debitAmount = null,
                creditAmount = baseValue,
                instrumentDebitAmount = null,
                instrumentCreditAmount = instrumentValue
            ),
            TransactionValidator.EntryData(
                accountId = toAccountId,
                debitAmount = baseValue,
                creditAmount = null,
                instrumentDebitAmount = instrumentValue,
                instrumentCreditAmount = null
            )
        )
    }
}
