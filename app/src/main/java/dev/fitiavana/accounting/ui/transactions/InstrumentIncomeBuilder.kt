package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountTypes

/**
 * Pure logic behind the "Instrument Income" transaction mode: income
 * received directly into an instrument-holding asset account, with a
 * revenue account as the other side. The base-currency side of the asset
 * entry is derived from the asset account's own prior balance/instrument-
 * balance ratio (see [InstrumentValueCalculator]), since there is no other
 * account to carry a rate from. The resulting entries are ordinary
 * [TransactionValidator.EntryData] pairs, so balance summary, validation and
 * saving stay shared with the "Classic" mode.
 */
object InstrumentIncomeBuilder {

    /**
     * The accounts offered in the asset spinner: asset accounts with an
     * instrument and no intermediary instrument. Input order is preserved.
     */
    fun selectableAssetAccounts(accounts: List<Account>): List<Account> =
        accounts.filter {
            it.type == AccountTypes.ASSET && it.instrumentCode != null && it.intermediaryInstrumentCode == null
        }

    /**
     * The accounts offered in the revenue spinner: revenue accounts with no
     * instrument and no intermediary instrument. Input order is preserved.
     */
    fun selectableRevenueAccounts(accounts: List<Account>): List<Account> =
        accounts.filter {
            it.type == AccountTypes.REVENUE && it.instrumentCode == null && it.intermediaryInstrumentCode == null
        }

    /**
     * The two entries an instrument income produces: [assetAccountId]
     * debited by [instrumentAmount] instrument units and [baseAmount]
     * base-currency units, and [revenueAccountId] credited by [baseAmount]
     * base-currency units only (revenue accounts hold no instrument). A null
     * or non-positive [instrumentAmount]/[baseAmount] yields null amounts,
     * which [TransactionValidator.validate] reports as incomplete.
     */
    fun buildEntries(
        assetAccountId: String,
        revenueAccountId: String,
        instrumentAmount: Long?,
        baseAmount: Long?
    ): List<TransactionValidator.EntryData> {
        val instrumentValue = instrumentAmount?.takeIf { it > 0L }
        val baseValue = baseAmount?.takeIf { it > 0L }
        return listOf(
            TransactionValidator.EntryData(
                accountId = assetAccountId,
                debitAmount = baseValue,
                creditAmount = null,
                instrumentDebitAmount = instrumentValue,
                instrumentCreditAmount = null
            ),
            TransactionValidator.EntryData(
                accountId = revenueAccountId,
                debitAmount = null,
                creditAmount = baseValue,
                instrumentDebitAmount = null,
                instrumentCreditAmount = null
            )
        )
    }
}
