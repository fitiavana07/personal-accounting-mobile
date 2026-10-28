package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountTypes

/**
 * Pure logic behind the "Swap" transaction mode: buying an amount of the To
 * account's instrument with an amount of the From account's instrument. From is
 * an asset with an instrument and no intermediary instrument; To is an asset
 * with a different instrument whose intermediary instrument is From's
 * instrument, so the amount spent is carried as To's intermediary cost basis.
 */
object SwapBuilder {

    private fun isSwapFrom(account: Account): Boolean =
        account.type == AccountTypes.ASSET &&
            account.instrumentCode != null &&
            account.intermediaryInstrumentCode == null

    /**
     * The accounts offered in the From spinner: swap-eligible assets whose base
     * balance in [balances] is not zero (or unknown) and that have at least one
     * To account in [accounts]. Input order is preserved.
     */
    fun selectableFromAccounts(accounts: List<Account>, balances: Map<String, Long>): List<Account> =
        accounts.filter {
            isSwapFrom(it) &&
                (balances[it.id] ?: 0L) != 0L &&
                selectableToAccounts(accounts, it).isNotEmpty()
        }

    /**
     * The accounts offered in the To spinner: assets with an instrument other
     * than From's and From's instrument as intermediary. Empty when no From
     * account is selected yet.
     */
    fun selectableToAccounts(accounts: List<Account>, fromAccount: Account?): List<Account> {
        if (fromAccount == null) return emptyList()
        return accounts.filter {
            it.type == AccountTypes.ASSET &&
                it.instrumentCode != null &&
                it.instrumentCode != fromAccount.instrumentCode &&
                it.intermediaryInstrumentCode == fromAccount.instrumentCode
        }
    }

    /**
     * The two entries of a swap: [fromAccountId] credited [fromAmount] units of
     * its instrument and [baseAmount] base units; [toAccountId] debited
     * [toAmount] units of its instrument, [fromAmount] of its intermediary
     * instrument and the same [baseAmount]. A null or non-positive amount yields
     * null, which [TransactionValidator.validate] reports as incomplete.
     */
    fun buildEntries(
        fromAccountId: String,
        toAccountId: String,
        fromAmount: Long?,
        toAmount: Long?,
        baseAmount: Long?
    ): List<TransactionValidator.EntryData> {
        val from = fromAmount?.takeIf { it > 0L }
        val to = toAmount?.takeIf { it > 0L }
        val base = baseAmount?.takeIf { it > 0L }
        return listOf(
            TransactionValidator.EntryData(
                accountId = fromAccountId,
                debitAmount = null,
                creditAmount = base,
                instrumentCreditAmount = from
            ),
            TransactionValidator.EntryData(
                accountId = toAccountId,
                debitAmount = base,
                creditAmount = null,
                instrumentDebitAmount = to,
                intermediaryDebitAmount = from
            )
        )
    }
}
