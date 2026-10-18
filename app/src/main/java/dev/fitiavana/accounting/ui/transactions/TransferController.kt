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
import dev.fitiavana.accounting.features.balances.BalanceCalculator
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.templates.TemplateSlot
import dev.fitiavana.accounting.features.templates.TemplateSlots
import dev.fitiavana.accounting.features.templates.TemplateWithEntries
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import dev.fitiavana.accounting.ui.common.baseAmountNoWrap
import dev.fitiavana.accounting.ui.common.emphasizeMainAmount
import dev.fitiavana.accounting.ui.common.enableAmountFormatting
import dev.fitiavana.accounting.ui.common.plainText
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Owns the Transfer mode's two account sides and shared amount field. What the
 * form does follows the From account:
 *  - an instrument-free account transfers a whole base-currency amount to
 *    another instrument-free account;
 *  - an account with an instrument transfers an amount of that instrument
 *    (decimals allowed, with a code label and a base-currency preview) to an
 *    account sharing its instrument and intermediary instrument.
 * See [TransferBuilder] for the pure account-filtering and entry logic, and
 * [AccountSideController] for the account-selection/balance-loading plumbing
 * shared with [InstrumentIncomeController].
 */
class TransferController(
    private val context: Context,
    private val viewModel: AddTransactionViewModel,
    private val instrumentsMap: Map<String, Instrument>,
    fromSpinner: Spinner,
    fromTextBalance: TextView,
    private val fromTextNewBalance: TextView,
    private val fromTextZeroBalanceError: TextView,
    toSpinner: Spinner,
    toTextBalance: TextView,
    private val toTextNewBalance: TextView,
    private val textAmountCode: TextView,
    private val editTransferAmount: EditText,
    buttonAll: Button,
    private val textAmountBase: TextView,
    private val onChanged: () -> Unit,
    runInBackground: (() -> Unit) -> Unit,
    runOnUiThread: (() -> Unit) -> Unit
) {

    private val getBalance: (String) -> SideBalance? = { id -> viewModel.getBalance(id)?.toSideBalance() }

    /** All accounts loaded, from which [TransferBuilder] derives each spinner's options. */
    private var allAccounts: List<Account> = emptyList()

    /** The last From account picked, to notice when the amount's unit changes with it. */
    private var lastFromAccount: Account? = null

    private val from: AccountSideController = AccountSideController(
        context = context,
        instrumentsMap = instrumentsMap,
        getBalance = getBalance,
        spinner = fromSpinner,
        textBalance = fromTextBalance,
        isDebit = false,
        onAccountSelected = { account ->
            showAmountUnit()
            if (account != null) {
                val previous = lastFromAccount
                lastFromAccount = account
                // Ar and instrument units are not comparable, so a typed amount can't carry over.
                if (previous != null && previous.instrumentCode != account.instrumentCode) {
                    editTransferAmount.text = null
                }
            }
            repopulateToSpinner(account)
            if (account == null) {
                fromTextNewBalance.visibility = View.GONE
                fromTextZeroBalanceError.visibility = View.GONE
                updateAmountBasePreview()
            }
        },
        onBalanceLoaded = {
            refreshPreviews()
            fromTextZeroBalanceError.text =
                context.getString(R.string.error_transfer_from_zero_balance)
            fromTextZeroBalanceError.visibility = if (fromBalanceIsZero()) View.VISIBLE else View.GONE
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
            if (account == null) {
                toTextNewBalance.visibility = View.GONE
                updateAmountBasePreview()
            }
        },
        onBalanceLoaded = { refreshPreviews() },
        runInBackground = runInBackground,
        runOnUiThread = runOnUiThread
    )

    /** Labels the amount field with the From account's instrument code, or Ar for base currency. */
    private fun showAmountUnit() {
        textAmountCode.text = currentInstrument()?.code ?: context.getString(R.string.label_currency_ar)
    }

    init {
        showAmountUnit()
        editTransferAmount.enableAmountFormatting { currentInstrument() != null }
        editTransferAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refreshPreviews()
                onChanged()
            }
        })
        buttonAll.setOnClickListener { fillAmountWithFromBalance() }
    }

    private fun refreshPreviews() {
        updateNewBalances(from)
        updateNewBalances(to)
        updateAmountBasePreview()
    }

    /** The From account's instrument, or null when no account is picked or it holds only base currency. */
    private fun currentInstrument(): Instrument? =
        from.account?.instrumentCode?.let { instrumentsMap[it] }

    /** Zero means nothing to transfer: the instrument balance for an instrument account, else the base balance. */
    private fun fromBalanceIsZero(): Boolean =
        if (currentInstrument() != null) from.instrumentBalance == 0L else from.balance == 0L

    /** "ALL": sets the amount to the From account's whole balance (instrument units if it has one). */
    private fun fillAmountWithFromBalance() {
        if (from.account == null) return
        val instrument = currentInstrument()
        if (instrument == null) {
            if (from.balance <= 0L) return
            editTransferAmount.setText(from.balance.toString())
        } else {
            if (from.instrumentBalance <= 0L) return
            editTransferAmount.setText(
                TransactionDisplay.formatInstrumentValue(from.instrumentBalance, instrument)
            )
        }
    }

    /** The typed amount in the From account's unit: instrument units, or whole base currency. */
    private fun parsedAmount(instrument: Instrument?): Long {
        val text = editTransferAmount.plainText()
        if (instrument == null) return text.toLongOrNull() ?: 0L
        val factor = 10.0.pow(instrument.decimalPlaces)
        return text.toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
    }

    /** The base-currency amount moved: the typed amount itself, or derived from the From account's rate. */
    private fun computedBaseAmount(instrument: Instrument?): Long? =
        if (instrument == null) {
            parsedAmount(null)
        } else {
            InstrumentValueCalculator.computeBaseAmount(
                instrumentAmount = parsedAmount(instrument),
                balance = from.balance,
                instrumentBalance = from.instrumentBalance
            )
        }

    private fun intermediaryInstrument(): Instrument? =
        from.account?.intermediaryInstrumentCode?.let { instrumentsMap[it] }

    /**
     * The intermediary instrument units moved, derived like the base amount from
     * the From account's own prior ratio (intermediary balance per instrument
     * unit). Null when From has no intermediary instrument or no rate.
     */
    private fun computedIntermediaryAmount(instrument: Instrument): Long? {
        if (intermediaryInstrument() == null) return null
        return InstrumentValueCalculator.computeBaseAmount(
            instrumentAmount = parsedAmount(instrument),
            balance = from.intermediaryBalance,
            instrumentBalance = from.instrumentBalance
        )
    }

    private fun updateNewBalances(side: AccountSideController) {
        val account = side.account
        val instrument = currentInstrument()
        val textNewBalance = if (side === from) fromTextNewBalance else toTextNewBalance
        if (account == null) {
            textNewBalance.visibility = View.GONE
            return
        }
        val baseAmount = computedBaseAmount(instrument) ?: 0L
        val newBalance = BalanceCalculator.project(
            accountType = account.type,
            currentBalance = side.balance,
            debit = if (side.isDebit) baseAmount else 0L,
            credit = if (side.isDebit) 0L else baseAmount
        )
        val formattedBase = TransactionDisplay.formatAmount(newBalance)
        if (instrument == null) {
            textNewBalance.text = emphasizeMainAmount(
                context,
                context.getString(R.string.label_new_balance_ar, formattedBase),
                baseAmountNoWrap(newBalance)
            )
            textNewBalance.visibility = View.VISIBLE
            return
        }
        val instrumentAmount = parsedAmount(instrument)
        val newInstrumentBalance = BalanceCalculator.project(
            accountType = account.type,
            currentBalance = side.instrumentBalance,
            debit = if (side.isDebit) instrumentAmount else 0L,
            credit = if (side.isDebit) 0L else instrumentAmount
        )
        val formattedInstrument = TransactionDisplay.formatInstrumentAmountNoWrap(newInstrumentBalance, instrument)
        val intermediary = intermediaryInstrument()
        val line = if (intermediary != null) {
            val intermediaryAmount = computedIntermediaryAmount(instrument) ?: 0L
            val newIntermediaryBalance = BalanceCalculator.project(
                accountType = account.type,
                currentBalance = side.intermediaryBalance,
                debit = if (side.isDebit) intermediaryAmount else 0L,
                credit = if (side.isDebit) 0L else intermediaryAmount
            )
            context.getString(
                R.string.label_new_balance_ar_instrument_intermediary,
                formattedBase,
                formattedInstrument,
                TransactionDisplay.formatInstrumentAmountNoWrap(newIntermediaryBalance, intermediary)
            )
        } else {
            context.getString(R.string.label_new_balance_ar_instrument, formattedBase, formattedInstrument)
        }
        textNewBalance.text = emphasizeMainAmount(context, line, formattedInstrument)
        textNewBalance.visibility = View.VISIBLE
    }

    /** Shows the instrument amount typed so far converted to base currency, using the From account's rate. */
    private fun updateAmountBasePreview() {
        val instrument = currentInstrument()
        val baseAmount = if (instrument != null && parsedAmount(instrument) > 0L) {
            computedBaseAmount(instrument)
        } else {
            null
        }
        if (instrument == null || baseAmount == null) {
            textAmountBase.visibility = View.GONE
            return
        }
        val formattedBase = TransactionDisplay.formatAmount(baseAmount)
        val intermediary = intermediaryInstrument()
        val intermediaryAmount = computedIntermediaryAmount(instrument)
        textAmountBase.text = if (intermediary != null && intermediaryAmount != null) {
            context.getString(
                R.string.label_amount_base_ar_instrument_intermediary,
                formattedBase,
                TransactionDisplay.formatInstrumentAmountNoWrap(intermediaryAmount, intermediary)
            )
        } else {
            context.getString(R.string.label_amount_base_ar, formattedBase)
        }
        textAmountBase.visibility = View.VISIBLE
    }

    /** Offers the accounts of [TransferBuilder]; From hides those with a zero balance in [balances]. */
    fun populateSpinners(accounts: List<Account>, balances: Map<String, Long>) {
        allAccounts = accounts
        from.populate(TransferBuilder.selectableFromAccounts(accounts, balances))
        repopulateToSpinner(null)
    }

    /**
     * Offers the TO accounts that fit [fromAccount], keeping the current TO selection (or a template's
     * [pendingToAccountId]) when it is still offered and clearing it otherwise.
     */
    private fun repopulateToSpinner(fromAccount: Account?) {
        val keep = pendingToAccountId ?: to.selectedAccountId()
        pendingToAccountId = null
        to.populate(TransferBuilder.selectableToAccounts(allAccounts, fromAccount))
        if (keep == null || !to.select(keep)) to.clearSelection()
        toTextNewBalance.visibility = View.GONE
    }

    /** The TO account of a template being applied, selected once the TO list has been narrowed to FROM's. */
    private var pendingToAccountId: String? = null

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
        val allowedTo = TransferBuilder.selectableToAccounts(allAccounts, fromAccount)
        if (allowedTo.none { it.id == toId }) return false

        pendingToAccountId = toId
        from.select(fromId)
        // The FROM listener may only fire later (or not at all if FROM is unchanged), so narrow TO now.
        repopulateToSpinner(fromAccount)
        return true
    }

    private fun toast(messageRes: Int) =
        Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()

    /** The two entries for this transfer, or null with a Toast already shown if incomplete. */
    fun collectEntries(): List<TransactionValidator.EntryData>? {
        val fromAccount = from.account
        val toAccount = to.account
        if (fromAccount == null || toAccount == null) {
            toast(R.string.error_transfer_accounts_required)
            return null
        }
        val instrument = currentInstrument()
        if (instrument == null) {
            if (from.balance == 0L) {
                toast(R.string.error_transfer_from_zero_balance)
                return null
            }
            return TransferBuilder.buildBaseEntries(fromAccount.id, toAccount.id, parsedAmount(null))
        }
        val instrumentAmount = parsedAmount(instrument)
        if (instrumentAmount <= 0L) {
            toast(R.string.error_instrument_transfer_amount_required)
            return null
        }
        val baseAmount = computedBaseAmount(instrument)
        if (baseAmount == null) {
            toast(R.string.error_transfer_from_zero_balance)
            return null
        }
        return TransferBuilder.buildInstrumentEntries(
            fromAccountId = fromAccount.id,
            toAccountId = toAccount.id,
            instrumentAmount = instrumentAmount,
            baseAmount = baseAmount,
            intermediaryAmount = computedIntermediaryAmount(instrument)
        )
    }

    /** Entries as typed so far, for the running totals line — ignores validity. */
    fun summaryEntries(): List<TransactionValidator.EntryData> {
        val fromId = from.account?.id ?: ""
        val toId = to.account?.id ?: ""
        val instrument = currentInstrument()
            ?: return TransferBuilder.buildBaseEntries(fromId, toId, parsedAmount(null))
        return TransferBuilder.buildInstrumentEntries(
            fromAccountId = fromId,
            toAccountId = toId,
            instrumentAmount = parsedAmount(instrument),
            baseAmount = computedBaseAmount(instrument),
            intermediaryAmount = computedIntermediaryAmount(instrument)
        )
    }

    fun hasContent(): Boolean =
        editTransferAmount.text.toString().trim().isNotEmpty() ||
                from.spinner.selectedItemPosition > 0 ||
                to.spinner.selectedItemPosition > 0

    /** Resets both sides and the amount back to their initial, empty state. */
    fun clear() {
        editTransferAmount.text = null
        lastFromAccount = null
        from.clearSelection()
        repopulateToSpinner(null)
    }
}
