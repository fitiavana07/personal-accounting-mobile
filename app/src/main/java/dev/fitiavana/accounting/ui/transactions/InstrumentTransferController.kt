package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
 * and entry/amount calculation.
 */
class InstrumentTransferController(
    private val context: Context,
    private val viewModel: AddTransactionViewModel,
    private val instrumentsMap: Map<String, Instrument>,
    fromSpinner: Spinner,
    fromTextBalance: TextView,
    fromTextNewBalance: TextView,
    toSpinner: Spinner,
    toTextBalance: TextView,
    toTextNewBalance: TextView,
    private val textAmountCode: TextView,
    private val editTransferAmount: EditText,
    private val textAmountBase: TextView,
    private val onChanged: () -> Unit,
    private val runInBackground: (() -> Unit) -> Unit,
    private val runOnUiThread: (() -> Unit) -> Unit
) {

    /**
     * One side of a transfer, with its current and projected balances. The
     * From side is credited and the To side debited by the amount entered.
     */
    private class Side(
        val spinner: Spinner,
        val textBalance: TextView,
        val textNewBalance: TextView,
        val isDebit: Boolean,
        var accounts: List<Account> = emptyList(),
        var account: Account? = null,
        var balance: Long = 0L,
        var instrumentBalance: Long = 0L
    )

    private val from = Side(fromSpinner, fromTextBalance, fromTextNewBalance, isDebit = false)
    private val to = Side(toSpinner, toTextBalance, toTextNewBalance, isDebit = true)

    /** All accounts loaded, from which [InstrumentTransferBuilder] derives each spinner's options. */
    private var allAccounts: List<Account> = emptyList()

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
        setupSide(from)
        setupSide(to)
    }

    private fun setupSide(side: Side) {
        side.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val account = selectedAccount(side)
                side.account = account
                side.balance = 0L
                side.instrumentBalance = 0L
                if (side === from) {
                    textAmountCode.text = account?.instrumentCode?.let { instrumentsMap[it] }?.code ?: ""
                    repopulateToSpinner(account)
                }
                if (account == null) {
                    hideBalances(side)
                    updateAmountBasePreview()
                    return
                }
                loadBalance(side, account)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                side.account = null
                hideBalances(side)
                if (side === from) {
                    textAmountCode.text = ""
                    repopulateToSpinner(null)
                }
                updateAmountBasePreview()
            }
        }
    }

    private fun loadBalance(side: Side, account: Account) {
        runInBackground {
            val bal = viewModel.getBalance(account.id)
            runOnUiThread {
                // a newer selection may have won the race
                if (side.account?.id != account.id) return@runOnUiThread
                side.balance = bal?.balance ?: 0L
                side.instrumentBalance = bal?.instrumentBalance ?: 0L
                val instrument = account.instrumentCode?.let { instrumentsMap[it] }
                side.textBalance.text = if (instrument != null) {
                    context.getString(
                        R.string.label_balance_ar_instrument,
                        TransactionDisplay.formatAmount(side.balance),
                        TransactionDisplay.formatInstrumentAmount(side.instrumentBalance, instrument)
                    )
                } else {
                    context.getString(
                        R.string.label_balance_ar,
                        TransactionDisplay.formatAmount(side.balance)
                    )
                }
                side.textBalance.visibility = View.VISIBLE
                updateNewBalances(from)
                updateNewBalances(to)
                updateAmountBasePreview()
            }
        }
    }

    private fun hideBalances(side: Side) {
        side.textBalance.visibility = View.GONE
        side.textNewBalance.visibility = View.GONE
    }

    private fun currentInstrument(): Instrument? =
        from.account?.instrumentCode?.let { instrumentsMap[it] }

    private fun parsedInstrumentAmount(instrument: Instrument): Long {
        val factor = 10.0.pow(instrument.decimalPlaces)
        return editTransferAmount.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
    }

    private fun computedBaseAmount(instrument: Instrument): Long? =
        InstrumentTransferBuilder.computeBaseAmount(
            instrumentAmount = parsedInstrumentAmount(instrument),
            fromBalance = from.balance,
            fromInstrumentBalance = from.instrumentBalance
        )

    private fun updateNewBalances(side: Side) {
        val account = side.account
        val instrument = currentInstrument()
        if (account == null || instrument == null) {
            side.textNewBalance.visibility = View.GONE
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
        side.textNewBalance.text = context.getString(
            R.string.label_new_balance_ar_instrument,
            TransactionDisplay.formatAmount(newBalance),
            TransactionDisplay.formatInstrumentAmount(newInstrumentBalance, instrument)
        )
        side.textNewBalance.visibility = View.VISIBLE
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
        from.accounts = InstrumentTransferBuilder.selectableFromAccounts(accounts)
        val names = listOf(context.getString(R.string.spinner_select_account)) +
                from.accounts.map { it.name }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        from.spinner.adapter = adapter
        from.spinner.setSelection(0)
        repopulateToSpinner(null)
    }

    private fun repopulateToSpinner(fromAccount: Account?) {
        to.accounts = InstrumentTransferBuilder.selectableToAccounts(allAccounts, fromAccount)
        val names = listOf(context.getString(R.string.spinner_select_account)) +
                to.accounts.map { it.name }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        to.spinner.adapter = adapter
        to.spinner.setSelection(0)
        to.account = null
        to.balance = 0L
        to.instrumentBalance = 0L
        hideBalances(to)
    }

    private fun selectedAccount(side: Side): Account? {
        val position = side.spinner.selectedItemPosition
        return if (position > 0 && position <= side.accounts.size) {
            side.accounts[position - 1]
        } else {
            null
        }
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
        val baseAmount = InstrumentTransferBuilder.computeBaseAmount(
            instrumentAmount = instrumentAmount,
            fromBalance = from.balance,
            fromInstrumentBalance = from.instrumentBalance
        )
        if (baseAmount == null) {
            Toast.makeText(
                context,
                context.getString(R.string.error_instrument_transfer_no_rate),
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
}
