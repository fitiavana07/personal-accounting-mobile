package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
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
 * intermediary balance. See [SwapBuilder] for the account filtering and entry
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
    buttonAll: Button,
    private val toAmountCode: TextView,
    private val editToAmount: EditText,
    private val textAmountBase: TextView,
    private val textRate: TextView,
    private val onChanged: () -> Unit,
    runInBackground: (() -> Unit) -> Unit,
    runOnUiThread: (() -> Unit) -> Unit
) {

    private val getBalance: (String) -> SideBalance? = { id -> viewModel.getBalance(id)?.toSideBalance() }

    /** All accounts loaded, from which [SwapBuilder] derives each spinner's options. */
    private var allAccounts: List<Account> = emptyList()

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
            showAmountUnits()
            repopulateToSpinner(account)
            if (account == null) {
                fromTextZeroBalanceError.visibility = View.GONE
                refreshPreviews()
            }
        },
        onBalanceLoaded = {
            fromTextZeroBalanceError.text = context.getString(R.string.error_transfer_from_zero_balance)
            fromTextZeroBalanceError.visibility = if (from.instrumentBalance == 0L) View.VISIBLE else View.GONE
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
        editFromAmount.enableAmountFormatting { fromInstrument() != null }
        editToAmount.enableAmountFormatting { toInstrument() != null }
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
        buttonAll.setOnClickListener { fillFromAmountWithBalance() }
    }

    private fun fromInstrument(): Instrument? = from.account?.instrumentCode?.let { instrumentsMap[it] }

    private fun toInstrument(): Instrument? = to.account?.instrumentCode?.let { instrumentsMap[it] }

    private fun showAmountUnits() {
        fromAmountCode.text = fromInstrument()?.code.orEmpty()
        toAmountCode.text = toInstrument()?.code.orEmpty()
    }

    /** "ALL": sets the From amount to the From account's whole instrument balance. */
    private fun fillFromAmountWithBalance() {
        val instrument = fromInstrument() ?: return
        if (from.instrumentBalance <= 0L) return
        editFromAmount.setText(TransactionDisplay.formatInstrumentValue(from.instrumentBalance, instrument))
    }

    private fun fromAmount(): Long = editFromAmount.plainAmount(fromInstrument())

    private fun toAmount(): Long = editToAmount.plainAmount(toInstrument())

    /** The base-currency value of the amount spent, from the From account's own prior rate; null without one. */
    private fun computedBaseAmount(): Long? = InstrumentValueCalculator.computeBaseAmount(
        instrumentAmount = fromAmount(),
        balance = from.balance,
        instrumentBalance = from.instrumentBalance
    )

    private fun refreshPreviews() {
        updateNewBalances()
        updateAmountBasePreview()
        updateRatePreview()
    }

    /** "1 <To code> = <price> <From code>", from the two typed amounts; hidden until both are positive. */
    private fun updateRatePreview() {
        val fromInstrument = fromInstrument()
        val toInstrument = toInstrument()
        val rate = if (fromInstrument != null && toInstrument != null && fromAmount() > 0L && toAmount() > 0L) {
            TransactionDisplay.formatInstrumentExchangeRate(toAmount(), toInstrument, fromAmount(), fromInstrument)
        } else {
            null
        }
        textRate.text = rate.orEmpty()
        textRate.visibility = if (rate == null) View.GONE else View.VISIBLE
    }

    /**
     * Projects both sides after the swap: From loses the base value and the amount spent; To gains the
     * base value, the amount received and (as cost basis) the amount spent in its intermediary balance.
     */
    private fun updateNewBalances() {
        val fromAccount = from.account
        val fromInstrument = fromInstrument()
        if (fromAccount == null || fromInstrument == null) {
            fromTextNewBalance.visibility = View.GONE
        } else {
            val newBase = from.balance - (computedBaseAmount() ?: 0L)
            val newInstrument = from.instrumentBalance - fromAmount()
            showNewBalance(
                fromTextNewBalance,
                context.getString(
                    R.string.label_new_balance_ar_instrument,
                    TransactionDisplay.formatAmount(newBase),
                    TransactionDisplay.formatInstrumentAmountNoWrap(newInstrument, fromInstrument)
                ),
                TransactionDisplay.formatInstrumentAmountNoWrap(newInstrument, fromInstrument)
            )
        }
        val toAccount = to.account
        val toInstrument = toInstrument()
        val intermediary = toAccount?.intermediaryInstrumentCode?.let { instrumentsMap[it] }
        if (toAccount == null || toInstrument == null || intermediary == null) {
            toTextNewBalance.visibility = View.GONE
            return
        }
        val newInstrument = TransactionDisplay.formatInstrumentAmountNoWrap(
            to.instrumentBalance + toAmount(), toInstrument
        )
        showNewBalance(
            toTextNewBalance,
            context.getString(
                R.string.label_new_balance_ar_instrument_intermediary,
                TransactionDisplay.formatAmount(to.balance + (computedBaseAmount() ?: 0L)),
                newInstrument,
                TransactionDisplay.formatInstrumentAmountNoWrap(to.intermediaryBalance + fromAmount(), intermediary)
            ),
            newInstrument
        )
    }

    private fun showNewBalance(view: TextView, line: String, main: String) {
        view.text = emphasizeMainAmount(context, line, main)
        view.visibility = View.VISIBLE
    }

    private fun updateAmountBasePreview() {
        val baseAmount = if (fromAmount() > 0L) computedBaseAmount() else null
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

    /** The two entries for this swap, or null with a Toast already shown if incomplete. */
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
        return SwapBuilder.buildEntries(fromAccount.id, toAccount.id, fromAmount(), toAmount(), baseAmount)
    }

    /** Entries as typed so far, for the running totals line — ignores validity. */
    fun summaryEntries(): List<TransactionValidator.EntryData> = SwapBuilder.buildEntries(
        fromAccountId = from.account?.id ?: "",
        toAccountId = to.account?.id ?: "",
        fromAmount = fromAmount(),
        toAmount = toAmount(),
        baseAmount = computedBaseAmount()
    )

    fun hasContent(): Boolean =
        editFromAmount.text.toString().trim().isNotEmpty() ||
            editToAmount.text.toString().trim().isNotEmpty() ||
            from.spinner.selectedItemPosition > 0 ||
            to.spinner.selectedItemPosition > 0

    /** Resets both sides and the amounts back to their initial, empty state. */
    fun clear() {
        editFromAmount.text = null
        editToAmount.text = null
        from.clearSelection()
        repopulateToSpinner(null)
    }
}
