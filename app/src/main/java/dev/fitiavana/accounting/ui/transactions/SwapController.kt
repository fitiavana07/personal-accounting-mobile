package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.templates.TemplateSlot
import dev.fitiavana.accounting.features.templates.TemplateSlots
import dev.fitiavana.accounting.features.templates.TemplateWithEntries
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import dev.fitiavana.accounting.ui.common.emphasizeMainAmount
import dev.fitiavana.accounting.ui.common.enableAmountFormatting
import dev.fitiavana.accounting.ui.common.plainAmount

/**
 * Owns the Swap mode's two account sides and its two amount fields: the amount
 * of the From account's instrument spent and the amount of the To account's
 * instrument received. The base-currency value is derived from the From
 * account's own prior balance ratio, so the cost basis carries into To's
 * intermediary balance. When From is a base-currency account, the amount spent is
 * in base currency and is To's cost basis directly, and a secondary base-currency
 * fee can be added on top. See [SwapBuilder] for the account filtering and entry
 * logic, and [AccountSideController] for the shared account-selection plumbing.
 */
class SwapController(
    private val context: Context,
    viewModel: AddTransactionViewModel,
    private val instrumentsMap: Map<String, Instrument>,
    fromSpinner: Spinner,
    fromTextBalance: TextView,
    private val fromTextNewBalance: TextView,
    private val fromTextZeroBalanceError: TextView,
    toSpinner: Spinner,
    toTextBalance: TextView,
    private val toTextNewBalance: TextView,
    private val fromAmountCode: TextView,
    private val editFromAmount: EditText,
    private val buttonAll: Button,
    private val toAmountCode: TextView,
    private val editToAmount: EditText,
    private val textAmountBase: TextView,
    private val textRate: TextView,
    switchFee: CompoundButton,
    feeGroup: View,
    private val feeAmountCode: TextView,
    editFee: EditText,
    feeSpinner: Spinner,
    private val textFeePreview: TextView,
    private val textFeeReceived: TextView,
    private val secondaryFeeSection: View,
    switchSecondaryFee: CompoundButton,
    secondaryFeeGroup: View,
    secondaryFeeAmountCode: TextView,
    editSecondaryFee: EditText,
    secondaryFeeSpinner: Spinner,
    private val onChanged: () -> Unit,
    runInBackground: (() -> Unit) -> Unit,
    runOnUiThread: (() -> Unit) -> Unit
) {

    private val getBalance: (String) -> SideBalance? = { id -> viewModel.getBalance(id)?.toSideBalance() }

    /** All accounts loaded, from which [SwapBuilder] derives each spinner's options. */
    private var allAccounts: List<Account> = emptyList()

    /** The swap fee, deducted from the amount received (in To's instrument). */
    private val swapFee = FeeSection(
        context, switchFee, feeGroup, editFee, feeSpinner,
        onToggled = { refreshPreviews(); onChanged() },
        onAccountChosen = { refreshSecondaryFeeAccounts(); refreshPreviews() }
    )

    /** The secondary fee, added on top of a base-currency From amount (in base currency). */
    private val secondaryFee = FeeSection(
        context, switchSecondaryFee, secondaryFeeGroup, editSecondaryFee, secondaryFeeSpinner,
        onToggled = { refreshPreviews(); onChanged() },
        onAccountChosen = { refreshPreviews() }
    )

    /** Whether the current From is a base-currency account, to re-apply the fee defaults only when that changes. */
    private var fromWasBase = false

    /** The TO account of a template being applied, selected once the TO list has been narrowed to FROM's. */
    private var pendingToAccountId: String? = null

    private val from: AccountSideController = AccountSideController(
        context = context,
        instrumentsMap = instrumentsMap,
        getBalance = getBalance,
        spinner = fromSpinner,
        textBalance = fromTextBalance,
        isDebit = false,
        onAccountSelected = { account ->
            applyFromKind(account)
            showAmountUnits()
            repopulateToSpinner(account)
            if (account == null) {
                fromTextZeroBalanceError.visibility = View.GONE
                refreshPreviews()
            }
        },
        onBalanceLoaded = {
            fromTextZeroBalanceError.text = context.getString(R.string.error_transfer_from_zero_balance)
            val noRate = !isBaseFrom() && from.instrumentBalance == 0L
            fromTextZeroBalanceError.visibility = if (noRate) View.VISIBLE else View.GONE
            refreshPreviews()
        },
        runInBackground = runInBackground,
        runOnUiThread = runOnUiThread
    )

    private val to: AccountSideController = AccountSideController(
        context = context,
        instrumentsMap = instrumentsMap,
        getBalance = getBalance,
        spinner = toSpinner,
        textBalance = toTextBalance,
        isDebit = true,
        onAccountSelected = { account ->
            showAmountUnits()
            if (account == null) refreshPreviews()
        },
        onBalanceLoaded = { refreshPreviews() },
        runInBackground = runInBackground,
        runOnUiThread = runOnUiThread
    )

    init {
        showAmountUnits()
        secondaryFeeAmountCode.text = context.getString(R.string.label_currency_ar)
        editFromAmount.enableAmountFormatting { fromInstrument() != null }
        editToAmount.enableAmountFormatting { toInstrument() != null }
        swapFee.amountField.enableAmountFormatting { toInstrument() != null }
        secondaryFee.amountField.enableAmountFormatting()
        secondaryFeeSection.visibility = View.GONE
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refreshPreviews()
                onChanged()
            }
        }
        editFromAmount.addTextChangedListener(watcher)
        editToAmount.addTextChangedListener(watcher)
        swapFee.amountField.addTextChangedListener(watcher)
        secondaryFee.amountField.addTextChangedListener(watcher)
        buttonAll.setOnClickListener { fillFromAmountWithBalance() }
    }

    private fun fromInstrument(): Instrument? = from.account?.instrumentCode?.let { instrumentsMap[it] }

    private fun toInstrument(): Instrument? = to.account?.instrumentCode?.let { instrumentsMap[it] }

    /** True when From is a base-currency account: the amount spent is in base currency (see [SwapBuilder.isBaseFrom]). */
    private fun isBaseFrom(): Boolean = from.account?.let { SwapBuilder.isBaseFrom(it) } == true

    /**
     * Adapts the form to the kind of From [account]: the ALL button and the secondary fee exist only for the
     * kind that supports them. When the kind changes, both fee switches take its default (on for base currency,
     * off otherwise); moving between accounts of the same kind keeps what the user set.
     */
    private fun applyFromKind(account: Account?) {
        val base = account != null && SwapBuilder.isBaseFrom(account)
        buttonAll.visibility = if (base) View.GONE else View.VISIBLE
        secondaryFeeSection.visibility = if (base) View.VISIBLE else View.GONE
        if (base == fromWasBase) return
        fromWasBase = base
        swapFee.isOn = base
        secondaryFee.isOn = base
    }

    private fun showAmountUnits() {
        val ar = context.getString(R.string.label_currency_ar)
        fromAmountCode.text = fromInstrument()?.code ?: if (isBaseFrom()) ar else ""
        toAmountCode.text = toInstrument()?.code.orEmpty()
        feeAmountCode.text = toInstrument()?.code.orEmpty()
    }

    /** "ALL": sets the From amount to the From account's whole instrument balance. */
    private fun fillFromAmountWithBalance() {
        val instrument = fromInstrument() ?: return
        if (from.instrumentBalance <= 0L) return
        editFromAmount.setText(TransactionDisplay.formatInstrumentValue(from.instrumentBalance, instrument))
    }

    private fun fromAmount(): Long = editFromAmount.plainAmount(fromInstrument())

    private fun toAmount(): Long = editToAmount.plainAmount(toInstrument())

    /** The fee typed in To's instrument; 0 while the fee toggle is off or nothing is typed. */
    private fun feeAmount(): Long = if (swapFee.isOn) swapFee.amountField.plainAmount(toInstrument()) else 0L

    private fun selectedFeeAccountId(): String? = swapFee.selectedAccountId()

    /** The secondary fee typed in base currency; 0 while it is toggled off, hidden or empty. */
    private fun secondaryFeeAmount(): Long =
        if (isBaseFrom() && secondaryFee.isOn) secondaryFee.amountField.plainAmount(null) else 0L

    private fun selectedSecondaryFeeAccountId(): String? = secondaryFee.selectedAccountId()

    /** The fee's value, or null with no fee typed (or an invalid/unrated one, see [collectEntries]). */
    private fun computedFee(): SwapBuilder.SwapFee? =
        if (isBaseFrom()) {
            SwapBuilder.computeBaseSwapFee(
                expenseAccountId = selectedFeeAccountId().orEmpty(),
                feeAmount = feeAmount(),
                fromAmount = fromAmount(),
                toAmount = toAmount()
            )
        } else {
            SwapBuilder.computeFee(
                expenseAccountId = selectedFeeAccountId().orEmpty(),
                feeAmount = feeAmount(),
                fromAmount = fromAmount(),
                toAmount = toAmount(),
                fromBalance = from.balance,
                fromInstrumentBalance = from.instrumentBalance
            )
        }

    /** The secondary fee typed so far, or null without a positive amount (its account may still be missing). */
    private fun computedSecondaryFee(): SwapBuilder.SecondaryFee? =
        secondaryFeeAmount().takeIf { it > 0L }
            ?.let { SwapBuilder.SecondaryFee(selectedSecondaryFeeAccountId().orEmpty(), it) }

    /**
     * The base-currency value of the amount spent: the amount itself from a base-currency From, otherwise
     * from the From account's own prior rate (null without one).
     */
    private fun computedBaseAmount(): Long? =
        if (isBaseFrom()) {
            fromAmount()
        } else {
            InstrumentValueCalculator.computeBaseAmount(
                instrumentAmount = fromAmount(),
                balance = from.balance,
                instrumentBalance = from.instrumentBalance
            )
        }

    private fun refreshPreviews() {
        updateNewBalances()
        updateAmountBasePreview()
        updateRatePreview()
        updateFeePreview()
        updateFeeReceived()
    }

    /** "1 <To code> = <price> <From code>", from the two typed amounts; hidden until both are positive. */
    private fun updateRatePreview() {
        val fromInstrument = fromInstrument()
        val toInstrument = toInstrument()
        val bothTyped = fromAmount() > 0L && toAmount() > 0L
        val rate = when {
            toInstrument == null || !bothTyped -> null
            isBaseFrom() -> TransactionDisplay.formatExchangeRate(fromAmount(), toAmount(), toInstrument)
            fromInstrument != null ->
                TransactionDisplay.formatInstrumentExchangeRate(toAmount(), toInstrument, fromAmount(), fromInstrument)
            else -> null
        }
        textRate.text = rate.orEmpty()
        textRate.visibility = if (rate == null) View.GONE else View.VISIBLE
    }

    /**
     * Projects both sides after the swap: From loses the base value (and the secondary fee) and the amount
     * spent; To gains the base value, the amount received and (as cost basis) the amount spent in its
     * intermediary balance.
     */
    private fun updateNewBalances() {
        updateFromNewBalance()
        updateToNewBalance()
    }

    private fun updateFromNewBalance() {
        val fromAccount = from.account
        val fromInstrument = fromInstrument()
        // Shown in red when saving would be refused for lack of balance, see [overdrawsFrom].
        val overdrawn = overdrawsFrom(computedBaseAmount() ?: 0L)
        when {
            fromAccount == null -> fromTextNewBalance.visibility = View.GONE
            isBaseFrom() -> {
                val newBase = from.balance - fromAmount() - secondaryFeeAmount()
                val line = context.getString(R.string.label_new_balance_ar, TransactionDisplay.formatAmount(newBase))
                showNewBalance(fromTextNewBalance, line, TransactionDisplay.formatAmount(newBase), overdrawn)
            }
            fromInstrument == null -> fromTextNewBalance.visibility = View.GONE
            else -> {
                val newBase = from.balance - (computedBaseAmount() ?: 0L)
                val newInstrument = from.instrumentBalance - fromAmount()
                showNewBalance(
                    fromTextNewBalance,
                    context.getString(
                        R.string.label_new_balance_ar_instrument,
                        TransactionDisplay.formatAmount(newBase),
                        TransactionDisplay.formatInstrumentAmountNoWrap(newInstrument, fromInstrument)
                    ),
                    TransactionDisplay.formatInstrumentAmountNoWrap(newInstrument, fromInstrument),
                    overdrawn
                )
            }
        }
    }

    private fun updateToNewBalance() {
        val toAccount = to.account
        val toInstrument = toInstrument()
        val intermediary = toAccount?.intermediaryInstrumentCode?.let { instrumentsMap[it] }
        if (toAccount == null || toInstrument == null || (intermediary == null && !isBaseFrom())) {
            toTextNewBalance.visibility = View.GONE
            return
        }
        val fee = computedFee()
        val newInstrument = TransactionDisplay.formatInstrumentAmountNoWrap(
            to.instrumentBalance + toAmount() - (fee?.feeAmount ?: 0L), toInstrument
        )
        val newBase = TransactionDisplay.formatAmount(
            to.balance + (computedBaseAmount() ?: 0L) - (fee?.feeBaseAmount ?: 0L)
        )
        val line = if (intermediary == null) {
            context.getString(R.string.label_new_balance_ar_instrument, newBase, newInstrument)
        } else {
            context.getString(
                R.string.label_new_balance_ar_instrument_intermediary,
                newBase,
                newInstrument,
                TransactionDisplay.formatInstrumentAmountNoWrap(
                    to.intermediaryBalance + fromAmount() - (fee?.feeFromAmount ?: 0L), intermediary
                )
            )
        }
        showNewBalance(toTextNewBalance, line, newInstrument)
    }

    /**
     * "+ <To amount> · <intermediary amount> · <Ar> Ar": what To really gets once the fee is deducted, in
     * its instruments (no intermediary one for a base-currency From); hidden without a valid fee.
     */
    private fun updateFeeReceived() {
        val fee = computedFee()
        val toInstrument = toInstrument()
        val baseAmount = computedBaseAmount()
        val intermediary = to.account?.intermediaryInstrumentCode?.let { instrumentsMap[it] }
        if (fee == null || toInstrument == null || baseAmount == null || (intermediary == null && !isBaseFrom())) {
            textFeeReceived.visibility = View.GONE
            return
        }
        val received = TransactionDisplay.formatInstrumentAmountNoWrap(toAmount() - fee.feeAmount, toInstrument)
        val netBase = TransactionDisplay.formatAmount(baseAmount - fee.feeBaseAmount)
        val line = if (intermediary == null) {
            context.getString(R.string.label_amount_plus_ar_instrument, netBase, received)
        } else {
            context.getString(
                R.string.label_amount_plus_ar_instrument_intermediary,
                netBase,
                received,
                TransactionDisplay.formatInstrumentAmountNoWrap(fromAmount() - fee.feeFromAmount, intermediary)
            )
        }
        textFeeReceived.text = emphasizeMainAmount(context, line, received)
        textFeeReceived.visibility = View.VISIBLE
    }

    /** "Fee ≈ <From units> · <Ar> Ar": the fee's value in From's instrument and base currency; hidden without a valid fee. */
    private fun updateFeePreview() {
        val fee = computedFee()
        val fromInstrument = fromInstrument()
        val text = when {
            fee == null -> null
            isBaseFrom() -> context.getString(
                R.string.label_swap_fee_preview_base, TransactionDisplay.formatAmount(fee.feeBaseAmount)
            )
            fromInstrument == null -> null
            else -> context.getString(
                R.string.label_swap_fee_preview,
                TransactionDisplay.formatInstrumentAmountNoWrap(fee.feeFromAmount, fromInstrument),
                TransactionDisplay.formatAmount(fee.feeBaseAmount)
            )
        }
        textFeePreview.text = text.orEmpty()
        textFeePreview.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun showNewBalance(view: TextView, line: String, main: String, negative: Boolean = false) {
        view.text = emphasizeMainAmount(context, line, main, negative)
        view.visibility = View.VISIBLE
    }

    /** "≈ Ar": the derived base value of an instrument amount; pointless (hidden) when the amount is already in Ar. */
    private fun updateAmountBasePreview() {
        val baseAmount = if (fromAmount() > 0L && !isBaseFrom()) computedBaseAmount() else null
        if (baseAmount == null) {
            textAmountBase.visibility = View.GONE
            return
        }
        textAmountBase.text = context.getString(R.string.label_amount_base_ar, TransactionDisplay.formatAmount(baseAmount))
        textAmountBase.visibility = View.VISIBLE
    }

    /** Offers the accounts of [SwapBuilder]; From hides those with a zero balance in [balances]. */
    fun populateSpinners(accounts: List<Account>, balances: Map<String, Long>) {
        allAccounts = accounts
        from.populate(SwapBuilder.selectableFromAccounts(accounts, balances))
        repopulateToSpinner(null)
        swapFee.populate(FeeAccounts.selectable(accounts))
        refreshSecondaryFeeAccounts()
    }

    /**
     * Offers the expense accounts for the secondary fee, minus the swap fee's account so one account can't
     * take both. Keeps the current choice when it is still offered, resets it to "Select account" otherwise.
     */
    private fun refreshSecondaryFeeAccounts() {
        secondaryFee.populate(FeeAccounts.selectableExcluding(allAccounts, swapFee.selectedAccountId()))
    }

    /**
     * Offers the TO accounts that fit [fromAccount], keeping the current TO selection (or a template's
     * [pendingToAccountId]) when it is still offered and clearing it otherwise.
     */
    private fun repopulateToSpinner(fromAccount: Account?) {
        val keep = pendingToAccountId ?: to.selectedAccountId()
        pendingToAccountId = null
        to.populate(SwapBuilder.selectableToAccounts(allAccounts, fromAccount))
        if (keep == null || !to.select(keep)) to.clearSelection()
    }

    /** The chosen FROM and TO accounts for saving as a template, or null until both are picked. */
    fun templateSlots(): List<TemplateSlot>? {
        val fromId = from.selectedAccountId() ?: return null
        val toId = to.selectedAccountId() ?: return null
        return listOf(TemplateSlot(TemplateSlots.FROM, fromId), TemplateSlot(TemplateSlots.TO, toId))
    }

    /** Selects the template's accounts; false, changing nothing, when one is gone or TO no longer fits FROM. */
    fun applyTemplate(template: TemplateWithEntries): Boolean {
        val fromId = template.accountFor(TemplateSlots.FROM) ?: return false
        val toId = template.accountFor(TemplateSlots.TO) ?: return false
        val fromAccount = from.accounts.firstOrNull { it.id == fromId } ?: return false
        if (SwapBuilder.selectableToAccounts(allAccounts, fromAccount).none { it.id == toId }) return false

        pendingToAccountId = toId
        from.select(fromId)
        // The FROM listener may only fire later (or not at all if FROM is unchanged), so narrow TO now.
        repopulateToSpinner(fromAccount)
        return true
    }

    private fun toast(messageRes: Int) =
        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()

    /** The entries for this swap, or null with a Toast already shown if incomplete. */
    fun collectEntries(): List<TransactionValidator.EntryData>? {
        val fromAccount = from.account
        val toAccount = to.account
        if (fromAccount == null || toAccount == null) {
            toast(R.string.error_transfer_accounts_required)
            return null
        }
        if (fromAmount() <= 0L || toAmount() <= 0L) {
            toast(R.string.error_swap_amounts_required)
            return null
        }
        val baseAmount = computedBaseAmount()
        if (baseAmount == null) {
            toast(R.string.error_transfer_from_zero_balance)
            return null
        }
        var fee: SwapBuilder.SwapFee? = null
        if (feeAmount() > 0L) {
            fee = computedFee()
            if (fee == null) {
                toast(R.string.error_swap_fee_invalid)
                return null
            }
            if (selectedFeeAccountId() == null) {
                toast(R.string.error_swap_fee_account_required)
                return null
            }
        }
        if (secondaryFeeAmount() > 0L && selectedSecondaryFeeAccountId() == null) {
            toast(R.string.error_swap_secondary_fee_account_required)
            return null
        }
        if (overdrawsFrom(baseAmount)) {
            toast(R.string.error_swap_insufficient_balance)
            return null
        }
        return buildEntries(fromAccount.id, toAccount.id, baseAmount, fee, computedSecondaryFee())
    }

    /**
     * True when the swap would take From below zero: its base balance (the base value spent plus the
     * secondary fee), or, for an instrument From, its instrument balance. Spending exactly the balance is fine.
     */
    private fun overdrawsFrom(baseAmount: Long): Boolean =
        baseAmount + secondaryFeeAmount() > from.balance ||
            (!isBaseFrom() && fromAmount() > from.instrumentBalance)

    /** Entries as typed so far, for the running totals line — ignores validity. */
    fun summaryEntries(): List<TransactionValidator.EntryData> = buildEntries(
        fromAccountId = from.account?.id ?: "",
        toAccountId = to.account?.id ?: "",
        baseAmount = computedBaseAmount(),
        fee = computedFee(),
        secondaryFee = computedSecondaryFee()
    )

    private fun buildEntries(
        fromAccountId: String,
        toAccountId: String,
        baseAmount: Long?,
        fee: SwapBuilder.SwapFee?,
        secondaryFee: SwapBuilder.SecondaryFee?
    ): List<TransactionValidator.EntryData> =
        if (isBaseFrom()) {
            SwapBuilder.buildBaseEntries(fromAccountId, toAccountId, fromAmount(), toAmount(), fee, secondaryFee)
        } else {
            SwapBuilder.buildEntries(fromAccountId, toAccountId, fromAmount(), toAmount(), baseAmount, fee)
        }

    fun hasContent(): Boolean =
        editFromAmount.text.toString().trim().isNotEmpty() ||
            editToAmount.text.toString().trim().isNotEmpty() ||
            secondaryFee.amountField.text.toString().trim().isNotEmpty() ||
            swapFee.isOn ||
            from.spinner.selectedItemPosition > 0 ||
            to.spinner.selectedItemPosition > 0

    /** Resets both sides and the amounts back to their initial, empty state. */
    fun clear() {
        editFromAmount.text = null
        editToAmount.text = null
        swapFee.isOn = false
        secondaryFee.isOn = false
        from.clearSelection()
        applyFromKind(null)
        repopulateToSpinner(null)
    }
}
