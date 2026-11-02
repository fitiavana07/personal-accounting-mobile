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
     * A transfer fee deducted from the received amount: [feeAmount] units of the transferred unit
     * (base currency, or From's instrument), worth [feeBaseAmount] base units and, when From has an
     * intermediary instrument, [feeIntermediaryAmount] of it; expensed to [expenseAccountId].
     */
    data class TransferFee(
        val expenseAccountId: String,
        val feeAmount: Long,
        val feeBaseAmount: Long,
        val feeIntermediaryAmount: Long?
    )

    /** Fee of a base-currency transfer: the fee is its own base value. Null unless in (0, [amount]). */
    fun computeBaseFee(expenseAccountId: String, feeAmount: Long, amount: Long): TransferFee? {
        if (feeAmount <= 0L || feeAmount >= amount) return null
        return TransferFee(expenseAccountId, feeAmount, feeAmount, null)
    }

    /**
     * Fee of an instrument transfer: [feeAmount] instrument units valued in base currency (and in
     * the intermediary instrument when [hasIntermediary]) at From's own prior ratios. Null unless
     * the fee is in (0, [amount]) or From has no rate.
     */
    fun computeInstrumentFee(
        expenseAccountId: String,
        feeAmount: Long,
        amount: Long,
        fromBalance: Long,
        fromInstrumentBalance: Long,
        fromIntermediaryBalance: Long,
        hasIntermediary: Boolean
    ): TransferFee? {
        if (feeAmount <= 0L || feeAmount >= amount) return null
        val feeBase = InstrumentValueCalculator.computeBaseAmount(feeAmount, fromBalance, fromInstrumentBalance)
            ?: return null
        val feeIntermediary = if (hasIntermediary) {
            InstrumentValueCalculator.computeBaseAmount(feeAmount, fromIntermediaryBalance, fromInstrumentBalance)
        } else {
            null
        }
        return TransferFee(expenseAccountId, feeAmount, feeBase, feeIntermediary)
    }

    /** The expense entry of a [fee]: debits its base value to the expense account. */
    private fun feeEntry(fee: TransferFee) = TransactionValidator.EntryData(
        accountId = fee.expenseAccountId,
        debitAmount = fee.feeBaseAmount,
        creditAmount = null
    )

    /**
     * The entries of a base-currency transfer: [fromAccountId] credited by [amount] and
     * [toAccountId] debited by it, net of the [fee] when there is one (which a third entry
     * debits to the expense account). An [amount] of zero (or less) yields null amounts, which
     * [TransactionValidator.validate] reports as incomplete.
     */
    fun buildBaseEntries(
        fromAccountId: String,
        toAccountId: String,
        amount: Long,
        fee: TransferFee? = null
    ): List<TransactionValidator.EntryData> {
        val value = amount.takeIf { it > 0L }
        val entries = listOf(
            TransactionValidator.EntryData(
                accountId = fromAccountId,
                debitAmount = null,
                creditAmount = value
            ),
            TransactionValidator.EntryData(
                accountId = toAccountId,
                debitAmount = value?.minus(fee?.feeBaseAmount ?: 0L),
                creditAmount = null
            )
        )
        return if (fee == null) entries else entries + feeEntry(fee)
    }

    /**
     * The two entries of an instrument transfer: [fromAccountId] credited and
     * [toAccountId] debited by [instrumentAmount] instrument units and
     * [baseAmount] base-currency units. A null or non-positive amount yields
     * null, which [TransactionValidator.validate] reports as incomplete. A
     * positive [intermediaryAmount] is also credited on the From entry and
     * debited on the To entry. With a [fee], To is debited net of it (instrument, base and
     * intermediary amounts reduced by the fee's) and a third entry debits the fee's base value to
     * the expense account.
     */
    fun buildInstrumentEntries(
        fromAccountId: String,
        toAccountId: String,
        instrumentAmount: Long?,
        baseAmount: Long?,
        intermediaryAmount: Long? = null,
        fee: TransferFee? = null
    ): List<TransactionValidator.EntryData> {
        val instrumentValue = instrumentAmount?.takeIf { it > 0L }
        val baseValue = baseAmount?.takeIf { it > 0L }
        val intermediaryValue = intermediaryAmount?.takeIf { it > 0L }
        val entries = listOf(
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
                debitAmount = baseValue?.minus(fee?.feeBaseAmount ?: 0L),
                creditAmount = null,
                instrumentDebitAmount = instrumentValue?.minus(fee?.feeAmount ?: 0L),
                instrumentCreditAmount = null,
                intermediaryDebitAmount = intermediaryValue?.minus(fee?.feeIntermediaryAmount ?: 0L)
            )
        )
        return if (fee == null) entries else entries + feeEntry(fee)
    }
}
