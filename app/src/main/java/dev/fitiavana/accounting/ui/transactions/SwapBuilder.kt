package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountTypes
import kotlin.math.roundToLong

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

    /** A base-currency asset (no instrument, no intermediary): a swap From that buys To with base currency. */
    fun isBaseFrom(account: Account): Boolean =
        account.type == AccountTypes.ASSET &&
            account.instrumentCode == null &&
            account.intermediaryInstrumentCode == null

    /**
     * The accounts offered in the From spinner: swap-eligible assets (with an instrument, or in
     * base currency) whose base balance in [balances] is not zero (or unknown) and that have at
     * least one To account in [accounts]. Input order is preserved.
     */
    fun selectableFromAccounts(accounts: List<Account>, balances: Map<String, Long>): List<Account> =
        accounts.filter {
            (isSwapFrom(it) || isBaseFrom(it)) &&
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
     * A swap fee deducted from the received amount: [feeAmount] units of To's instrument, worth
     * [feeFromAmount] units of From's instrument and [feeBaseAmount] base units, expensed to
     * [expenseAccountId].
     */
    data class SwapFee(
        val expenseAccountId: String,
        val feeAmount: Long,
        val feeFromAmount: Long,
        val feeBaseAmount: Long
    )

    /**
     * Values [feeAmount] (To's instrument): in From's instrument at the swap's own price
     * ([fromAmount] per [toAmount]), then in base currency at From's own prior rate. Null when
     * the fee is not in (0, [toAmount]) or From has no rate.
     */
    fun computeFee(
        expenseAccountId: String,
        feeAmount: Long,
        fromAmount: Long,
        toAmount: Long,
        fromBalance: Long,
        fromInstrumentBalance: Long
    ): SwapFee? {
        val feeFrom = feeInFromUnits(feeAmount, fromAmount, toAmount) ?: return null
        val feeBase = InstrumentValueCalculator.computeBaseAmount(feeFrom, fromBalance, fromInstrumentBalance)
            ?: return null
        return SwapFee(expenseAccountId, feeAmount, feeFrom, feeBase)
    }

    /**
     * [feeAmount] (To's instrument) valued in From's unit at the swap's own price ([fromAmount] per
     * [toAmount]). Null when the fee is not in (0, [toAmount]) or [fromAmount] is not positive.
     */
    private fun feeInFromUnits(feeAmount: Long, fromAmount: Long, toAmount: Long): Long? {
        if (feeAmount <= 0L || feeAmount >= toAmount || fromAmount <= 0L) return null
        return (feeAmount.toDouble() * fromAmount.toDouble() / toAmount.toDouble()).roundToLong()
    }

    /**
     * Fee of a base-currency swap: [feeAmount] (To's instrument) valued at the swap's own price
     * ([fromAmount] base units per [toAmount]). The base value is also the From-unit value. Null
     * when the fee is not in (0, [toAmount]) or [fromAmount] is not positive.
     */
    fun computeBaseSwapFee(
        expenseAccountId: String,
        feeAmount: Long,
        fromAmount: Long,
        toAmount: Long
    ): SwapFee? {
        val feeBase = feeInFromUnits(feeAmount, fromAmount, toAmount) ?: return null
        return SwapFee(expenseAccountId, feeAmount, feeBase, feeBase)
    }

    /** A base-currency fee charged on top of a base-currency swap, expensed to [expenseAccountId]. */
    data class SecondaryFee(val expenseAccountId: String, val amount: Long)

    /**
     * The entries of a swap bought with base currency: [fromAccountId] credited [amount] base
     * units (plus the [secondaryFee], charged on top); [toAccountId] debited [toAmount] units of
     * its instrument and [amount] base units, which is its cost basis. With a [fee], To receives
     * net of it and an expense entry debits its base value. The [secondaryFee] has its own expense
     * entry. A non-positive [amount] or [toAmount] yields null amounts, which
     * [TransactionValidator.validate] reports as incomplete.
     */
    fun buildBaseEntries(
        fromAccountId: String,
        toAccountId: String,
        amount: Long,
        toAmount: Long,
        fee: SwapFee? = null,
        secondaryFee: SecondaryFee? = null
    ): List<TransactionValidator.EntryData> {
        val base = amount.takeIf { it > 0L }
        val to = toAmount.takeIf { it > 0L }
        val entries = listOf(
            TransactionValidator.EntryData(
                accountId = fromAccountId,
                debitAmount = null,
                creditAmount = base?.plus(secondaryFee?.amount ?: 0L)
            ),
            TransactionValidator.EntryData(
                accountId = toAccountId,
                debitAmount = base?.minus(fee?.feeBaseAmount ?: 0L),
                creditAmount = null,
                instrumentDebitAmount = to?.minus(fee?.feeAmount ?: 0L)
            )
        )
        return entries +
            listOfNotNull(
                fee?.let { FeeAccounts.expenseEntry(it.expenseAccountId, it.feeBaseAmount) },
                secondaryFee?.let { FeeAccounts.expenseEntry(it.expenseAccountId, it.amount) }
            )
    }

    /**
     * The entries of a swap: [fromAccountId] credited [fromAmount] units of
     * its instrument and [baseAmount] base units; [toAccountId] debited
     * [toAmount] units of its instrument, [fromAmount] of its intermediary
     * instrument and the same [baseAmount]. With a [fee], To receives net of it
     * (amounts and base reduced by the fee's) and a third entry debits the fee's
     * base value to the expense account. A null or non-positive amount yields
     * null, which [TransactionValidator.validate] reports as incomplete.
     */
    fun buildEntries(
        fromAccountId: String,
        toAccountId: String,
        fromAmount: Long?,
        toAmount: Long?,
        baseAmount: Long?,
        fee: SwapFee? = null
    ): List<TransactionValidator.EntryData> {
        val from = fromAmount?.takeIf { it > 0L }
        val to = toAmount?.takeIf { it > 0L }
        val base = baseAmount?.takeIf { it > 0L }
        val entries = listOf(
            TransactionValidator.EntryData(
                accountId = fromAccountId,
                debitAmount = null,
                creditAmount = base,
                instrumentCreditAmount = from
            ),
            TransactionValidator.EntryData(
                accountId = toAccountId,
                debitAmount = base?.minus(fee?.feeBaseAmount ?: 0L),
                creditAmount = null,
                instrumentDebitAmount = to?.minus(fee?.feeAmount ?: 0L),
                intermediaryDebitAmount = from?.minus(fee?.feeFromAmount ?: 0L)
            )
        )
        return if (fee == null) entries else entries + FeeAccounts.expenseEntry(fee.expenseAccountId, fee.feeBaseAmount)
    }
}
