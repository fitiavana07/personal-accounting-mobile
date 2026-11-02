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
        if (feeAmount <= 0L || feeAmount >= toAmount || fromAmount <= 0L) return null
        val feeFrom = (feeAmount.toDouble() * fromAmount.toDouble() / toAmount.toDouble()).roundToLong()
        val feeBase = InstrumentValueCalculator.computeBaseAmount(feeFrom, fromBalance, fromInstrumentBalance)
            ?: return null
        return SwapFee(expenseAccountId, feeAmount, feeFrom, feeBase)
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
        if (fee == null) return entries
        return entries + TransactionValidator.EntryData(
            accountId = fee.expenseAccountId,
            debitAmount = fee.feeBaseAmount,
            creditAmount = null
        )
    }
}
