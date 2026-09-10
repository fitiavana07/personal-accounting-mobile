package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account

/**
 * Pure logic behind the "Simple Transfer" transaction mode: a base-currency
 * amount moved between two instrument-free asset accounts. The resulting
 * entries are ordinary [TransactionValidator.EntryData] pairs, so balance
 * summary, validation and saving stay shared with the "Classic" mode.
 */
object SimpleTransferBuilder {

    private const val ASSET_TYPE = "asset"

    /**
     * The accounts offered in the From/To spinners: asset accounts that hold
     * no instrument, so the transfer only ever moves base-currency amounts.
     * Input order is preserved.
     */
    fun selectableAccounts(accounts: List<Account>): List<Account> =
        accounts.filter { it.type == ASSET_TYPE && it.instrumentCode == null }

    /**
     * The two entries a simple transfer produces: [fromAccountId] credited and
     * [toAccountId] debited by [amount]. An [amount] of zero (or less) yields
     * null amounts, which [TransactionValidator.validate] reports as
     * incomplete.
     */
    fun buildEntries(
        fromAccountId: String,
        toAccountId: String,
        amount: Long
    ): List<TransactionValidator.EntryData> {
        val value = if (amount > 0L) amount else null
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
}
