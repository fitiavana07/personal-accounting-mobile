package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.BalanceCalculator
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Owns the Instrument Transfer mode's two account sides and shared amount
 * field: wires account selection (the To side offered only accounts sharing
 * the From account's instrument), loads and previews each side's base and
 * instrument balances (one line each for current/new balance), and builds
 * the two-entry transfer once both accounts and an amount are chosen. See
 * [InstrumentTransferBuilder] for the pure logic behind account filtering
 * and entry/amount calculation, and [AccountSideController] for the
 * account-selection/balance-loading plumbing shared with
 * [InstrumentIncomeController].
 */
class InstrumentTransferController(
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
    private val textAmountBase: TextView,
    private val onChanged: () -> Unit,
    runInBackground: (() -> Unit) -> Unit,
    runOnUiThread: (() -> Unit) -> Unit
) {

    private val getBalance: (String) -> Pair<Long, Long>? = { id ->
        viewModel.getBalance(id)?.let { it.balance to it.instrumentBalance }
    }

    /** All accounts loaded, from which [InstrumentTransferBuilder] derives each spinner's options. */
    private var allAccounts: List<Account> = emptyList()

    private val from: AccountSideController = AccountSideController(
        context = context,
        instrumentsMap = instrumentsMap,
        getBalance = getBalance,
        spinner = fromSpinner,
        textBalance = fromTextBalance,
        isDebit = false,
        onAccountSelected = { account ->
            textAmountCode.text = account?.instrumentCode?.let { instrumentsMap[it] }?.code ?: ""
            repopulateToSpinner(account)
            if (account == null) {
                fromTextNewBalance.visibility = View.GONE
                fromTextZeroBalanceError.visibility = View.GONE
                updateAmountBasePreview()
            }
        },
        onBalanceLoaded = {
            updateNewBalances(from)
            updateNewBalances(to)
            updateAmountBasePreview()
            fromTextZeroBalanceError.text =
                context.getString(R.string.error_transfer_from_zero_balance)
            fromTextZeroBalanceError.visibility =
                if (from.instrumentBalance == 0L) View.VISIBLE else View.GONE
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
        onBalanceLoaded = {
            updateNewBalances(from)
            updateNewBalances(to)
            updateAmountBasePreview()
        },
        runInBackground = runInBackground,
        runOnUiThread = runOnUiThread
    )

    init {
        editTransferAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNewBalances(from)
                updateNewBalances(to)
                updateAmountBasePreview()
                onChanged()
            }
        })
    }

    private fun currentInstrument(): Instrument? =
        from.account?.instrumentCode?.let { instrumentsMap[it] }

    private fun parsedInstrumentAmount(instrument: Instrument): Long {
        val factor = 10.0.pow(instrument.decimalPlaces)
        return editTransferAmount.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
    }

    private fun computedBaseAmount(instrument: Instrument): Long? =
        InstrumentValueCalculator.computeBaseAmount(
            instrumentAmount = parsedInstrumentAmount(instrument),
            balance = from.balance,
            instrumentBalance = from.instrumentBalance
        )

    private fun updateNewBalances(side: AccountSideController) {
        val account = side.account
        val instrument = currentInstrument()
        val textNewBalance = if (side === from) fromTextNewBalance else toTextNewBalance
        if (account == null || instrument == null) {
            textNewBalance.visibility = View.GONE
            return
        }
        val instrumentAmount = parsedInstrumentAmount(instrument)
        val baseAmount = computedBaseAmount(instrument) ?: 0L

        val newBalance = BalanceCalculator.project(
            accountType = account.type,
            currentBalance = side.balance,
            debit = if (side.isDebit) baseAmount else 0L,
            credit = if (side.isDebit) 0L else baseAmount
        )
        val newInstrumentBalance = BalanceCalculator.project(
            accountType = account.type,
            currentBalance = side.instrumentBalance,
            debit = if (side.isDebit) instrumentAmount else 0L,
            credit = if (side.isDebit) 0L else instrumentAmount
        )
        textNewBalance.text = context.getString(
            R.string.label_new_balance_ar_instrument,
            TransactionDisplay.formatAmount(newBalance),
            TransactionDisplay.formatInstrumentAmount(newInstrumentBalance, instrument)
        )
        textNewBalance.visibility = View.VISIBLE
    }

    /** Shows the amount typed so far converted to base currency, using the From account's rate. */
    private fun updateAmountBasePreview() {
        val instrument = currentInstrument()
        val instrumentAmount = instrument?.let { parsedInstrumentAmount(it) } ?: 0L
        val baseAmount = if (instrument != null && instrumentAmount > 0L) {
            computedBaseAmount(instrument)
        } else {
            null
        }
        if (baseAmount == null) {
            textAmountBase.visibility = View.GONE
            return
        }
        textAmountBase.text = context.getString(
            R.string.label_amount_base_ar,
            TransactionDisplay.formatAmount(baseAmount)
        )
        textAmountBase.visibility = View.VISIBLE
    }

    fun populateSpinners(accounts: List<Account>) {
        allAccounts = accounts
        from.populate(InstrumentTransferBuilder.selectableFromAccounts(accounts))
        repopulateToSpinner(null)
    }

    private fun repopulateToSpinner(fromAccount: Account?) {
        to.populate(InstrumentTransferBuilder.selectableToAccounts(allAccounts, fromAccount))
        to.clearSelection()
        toTextNewBalance.visibility = View.GONE
    }

    /** The two entries for this transfer, or null with a Toast already shown if incomplete. */
    fun collectEntries(): List<TransactionValidator.EntryData>? {
        val fromAccount = from.account
        val toAccount = to.account
        if (fromAccount == null || toAccount == null) {
            Toast.makeText(
                context,
                context.getString(R.string.error_transfer_accounts_required),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        val instrument = currentInstrument()
        val instrumentAmount = instrument?.let { parsedInstrumentAmount(it) } ?: 0L
        if (instrumentAmount <= 0L) {
            Toast.makeText(
                context,
                context.getString(R.string.error_instrument_transfer_amount_required),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        val baseAmount = InstrumentValueCalculator.computeBaseAmount(
            instrumentAmount = instrumentAmount,
            balance = from.balance,
            instrumentBalance = from.instrumentBalance
        )
        if (baseAmount == null) {
            Toast.makeText(
                context,
                context.getString(R.string.error_transfer_from_zero_balance),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        return InstrumentTransferBuilder.buildEntries(
            fromAccountId = fromAccount.id,
            toAccountId = toAccount.id,
            instrumentAmount = instrumentAmount,
            baseAmount = baseAmount
        )
    }

    /** Entries as typed so far, for the running totals line — ignores validity. */
    fun summaryEntries(): List<TransactionValidator.EntryData> {
        val instrument = currentInstrument()
        val instrumentAmount = instrument?.let { parsedInstrumentAmount(it) } ?: 0L
        val baseAmount = instrument?.let { computedBaseAmount(it) }
        return InstrumentTransferBuilder.buildEntries(
            fromAccountId = from.account?.id ?: "",
            toAccountId = to.account?.id ?: "",
            instrumentAmount = instrumentAmount,
            baseAmount = baseAmount
        )
    }

    fun hasContent(): Boolean =
        editTransferAmount.text.toString().trim().isNotEmpty() ||
                from.spinner.selectedItemPosition > 0 ||
                to.spinner.selectedItemPosition > 0

    /** Resets both sides and the amount back to their initial, empty state. */
    fun clear() {
        editTransferAmount.text = null
        from.clearSelection()
        repopulateToSpinner(null)
    }
}
