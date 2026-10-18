package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountTypes

/**
 * Pure logic behind the "Transfer" transaction mode. What the transfer looks
 * like depends on the From account:
 *  - an instrument-free asset moves a base-currency amount to another
 *    instrument-free asset ([buildBaseEntries]);
 *  - an asset with an instrument moves an amount of that instrument to an asset
 *    sharing its instrument and intermediary instrument, the base (and
 *    intermediary) side being derived from the From account's own prior
 *    balance ratio so it carries the cost basis ([buildInstrumentEntries]).
 * The resulting entries are ordinary [TransactionValidator.EntryData] pairs, so
 * balance summary, validation and saving stay shared with the "Classic" mode.
 */
object TransferBuilder {

    private fun assets(accounts: List<Account>): List<Account> =
        accounts.filter { it.type == AccountTypes.ASSET }

    /**
     * The accounts offered in the From spinner: asset accounts, with or without
     * an instrument, whose base balance in [balances] is not zero (or unknown).
     * Input order is preserved.
     */
    fun selectableFromAccounts(accounts: List<Account>, balances: Map<String, Long>): List<Account> =
        assets(accounts).filter { (balances[it.id] ?: 0L) != 0L }

    /**
     * The accounts offered in the To spinner, excluding [fromAccount] itself,
     * whatever their balance: instrument-free assets when From has no
     * instrument, otherwise assets with From's instrument and intermediary
     * instrument. Empty when no From account is selected yet.
     */
    fun selectableToAccounts(accounts: List<Account>, fromAccount: Account?): List<Account> {
        if (fromAccount == null) return emptyList()
        return assets(accounts).filter {
            it.id != fromAccount.id &&
                it.instrumentCode == fromAccount.instrumentCode &&
                it.intermediaryInstrumentCode == fromAccount.intermediaryInstrumentCode
        }
    }

    /**
     * The two entries of a base-currency transfer: [fromAccountId] credited and
     * [toAccountId] debited by [amount]. An [amount] of zero (or less) yields
     * null amounts, which [TransactionValidator.validate] reports as incomplete.
     */
    fun buildBaseEntries(
        fromAccountId: String,
        toAccountId: String,
        amount: Long
    ): List<TransactionValidator.EntryData> {
        val value = amount.takeIf { it > 0L }
        return listOf(
            TransactionValidator.EntryData(
                accountId = fromAccountId,
                debitAmount = null,
                creditAmount = value
            ),
            TransactionValidator.EntryData(
                accountId = toAccountId,
                debitAmount = value,
                creditAmount = null
            )
        )
    }

    /**
     * The two entries of an instrument transfer: [fromAccountId] credited and
     * [toAccountId] debited by [instrumentAmount] instrument units and
     * [baseAmount] base-currency units. A null or non-positive amount yields
     * null, which [TransactionValidator.validate] reports as incomplete. A
     * positive [intermediaryAmount] is also credited on the From entry and
     * debited on the To entry.
     */
    fun buildInstrumentEntries(
        fromAccountId: String,
        toAccountId: String,
        instrumentAmount: Long?,
        baseAmount: Long?,
        intermediaryAmount: Long? = null
    ): List<TransactionValidator.EntryData> {
        val instrumentValue = instrumentAmount?.takeIf { it > 0L }
        val baseValue = baseAmount?.takeIf { it > 0L }
        val intermediaryValue = intermediaryAmount?.takeIf { it > 0L }
        return listOf(
            TransactionValidator.EntryData(
                accountId = fromAccountId,
                debitAmount = null,
                creditAmount = baseValue,
                instrumentDebitAmount = null,
                instrumentCreditAmount = instrumentValue,
                intermediaryCreditAmount = intermediaryValue
            ),
            TransactionValidator.EntryData(
                accountId = toAccountId,
                debitAmount = baseValue,
                creditAmount = null,
                instrumentDebitAmount = instrumentValue,
                instrumentCreditAmount = null,
                intermediaryDebitAmount = intermediaryValue
            )
        )
    }
}
