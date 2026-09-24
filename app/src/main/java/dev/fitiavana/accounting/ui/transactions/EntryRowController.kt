package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceCalculator
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Owns one Classic-mode entry row: inflates [R.layout.item_entry_row], wires
 * its account/amount inputs, and tracks the balances needed to preview the
 * row's effect once an account and amounts are chosen.
 */
class EntryRowController(
    private val context: Context,
    layoutInflater: LayoutInflater,
    parent: ViewGroup,
    private val viewModel: AddTransactionViewModel,
    private val accounts: List<Account>,
    private val instrumentsMap: Map<String, Instrument>,
    private val onChanged: () -> Unit,
    private val onRemoveClicked: (EntryRowController) -> Unit,
    private val runInBackground: (() -> Unit) -> Unit,
    private val runOnUiThread: (() -> Unit) -> Unit
) {

    sealed class EntryResult {
        data class Success(val data: TransactionValidator.EntryData) : EntryResult()
        object Incomplete : EntryResult()
        data class InstrumentAmountRequired(val instrumentCode: String) : EntryResult()
    }

    val view: View = layoutInflater.inflate(R.layout.item_entry_row, parent, false)

    val spinner: Spinner = view.findViewById(R.id.spinner_account)
    val editDebit: EditText = view.findViewById(R.id.edit_debit)
    val editCredit: EditText = view.findViewById(R.id.edit_credit)
    val btnRemove: ImageButton = view.findViewById(R.id.btn_remove_entry)
    val editInstrumentDebit: EditText = view.findViewById(R.id.edit_instrument_debit)
    val editInstrumentCredit: EditText = view.findViewById(R.id.edit_instrument_credit)
    private val instrumentRow: View = view.findViewById(R.id.row_instrument_amounts)
    private val textInstrumentCode: TextView = view.findViewById(R.id.text_instrument_code)
    val editIntermediaryDebit: EditText = view.findViewById(R.id.edit_intermediary_debit)
    val editIntermediaryCredit: EditText = view.findViewById(R.id.edit_intermediary_credit)
    private val intermediaryRow: View = view.findViewById(R.id.row_intermediary_amounts)
    private val textIntermediaryCode: TextView =
        view.findViewById(R.id.text_intermediary_instrument_code)
    private val textBalanceRow: View = view.findViewById(R.id.text_balance_row)
    private val textBalance: TextView = view.findViewById(R.id.text_balance)
    private val textInstrumentBalanceRow: View =
        view.findViewById(R.id.text_instrument_balance_row)
    private val textInstrumentBalance: TextView =
        view.findViewById(R.id.text_instrument_balance)
    private val textIntermediaryBalanceRow: View =
        view.findViewById(R.id.text_intermediary_balance_row)
    private val textIntermediaryBalance: TextView =
        view.findViewById(R.id.text_intermediary_balance)
    private val textNewBalanceRow: View = view.findViewById(R.id.text_new_balance_row)
    private val textNewBalance: TextView = view.findViewById(R.id.text_new_balance)
    private val textNewInstrumentBalanceRow: View =
        view.findViewById(R.id.text_new_instrument_balance_row)
    private val textNewInstrumentBalance: TextView =
        view.findViewById(R.id.text_new_instrument_balance)
    private val textNewIntermediaryBalanceRow: View =
        view.findViewById(R.id.text_new_intermediary_balance_row)
    private val textNewIntermediaryBalance: TextView =
        view.findViewById(R.id.text_new_intermediary_balance)

    private var currentBalance: Long = 0L
    private var currentAccountType: String = ""
    private var currentInstrumentBalance: Long = 0L
    private var currentInstrument: Instrument? = null
    private var currentIntermediaryBalance: Long = 0L
    private var currentIntermediaryInstrument: Instrument? = null

    init {
        val accountNames =
            listOf(context.getString(R.string.spinner_select_account)) + accounts.map { it.name }
        val spinnerAdapter = ArrayAdapter(context, R.layout.item_spinner_small, accountNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter
        spinner.setSelection(0)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                onAccountSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                clearAccountDependentState()
            }
        }

        editInstrumentDebit.addTextChangedListener(
            mutuallyExclusiveWatcher(editInstrumentCredit) { updateNewInstrumentBalance() }
        )
        editInstrumentCredit.addTextChangedListener(
            mutuallyExclusiveWatcher(editInstrumentDebit) { updateNewInstrumentBalance() }
        )
        editIntermediaryDebit.addTextChangedListener(
            mutuallyExclusiveWatcher(editIntermediaryCredit) { updateNewIntermediaryBalance() }
        )
        editIntermediaryCredit.addTextChangedListener(
            mutuallyExclusiveWatcher(editIntermediaryDebit) { updateNewIntermediaryBalance() }
        )
        editDebit.addTextChangedListener(
            mutuallyExclusiveWatcher(editCredit) {
                updateNewBalance()
                onChanged()
            }
        )
        editCredit.addTextChangedListener(
            mutuallyExclusiveWatcher(editDebit) {
                updateNewBalance()
                onChanged()
            }
        )

        ImageViewCompat.setImageTintList(
            btnRemove,
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.icon_remove))
        )
        btnRemove.setOnClickListener { onRemoveClicked(this) }
    }

    private fun mutuallyExclusiveWatcher(
        other: EditText,
        onAfterChanged: () -> Unit
    ): TextWatcher = object : TextWatcher {
        var updating = false
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (!updating && !s.isNullOrEmpty()) {
                updating = true
                other.text = null
                updating = false
            }
            onAfterChanged()
        }
    }

    private fun onAccountSelected(position: Int) {
        val account =
            if (position > 0 && position <= accounts.size) accounts[position - 1] else null
        val instrument = account?.instrumentCode?.let { instrumentsMap[it] }
        if (instrument != null) {
            textInstrumentCode.text = instrument.code
            instrumentRow.visibility = View.VISIBLE
        } else {
            editInstrumentDebit.text = null
            editInstrumentCredit.text = null
            instrumentRow.visibility = View.GONE
            textInstrumentBalanceRow.visibility = View.GONE
            textNewInstrumentBalanceRow.visibility = View.GONE
        }
        val intermediaryInstrument = account?.intermediaryInstrumentCode?.let { instrumentsMap[it] }
        if (intermediaryInstrument != null) {
            textIntermediaryCode.text = intermediaryInstrument.code
            intermediaryRow.visibility = View.VISIBLE
        } else {
            editIntermediaryDebit.text = null
            editIntermediaryCredit.text = null
            intermediaryRow.visibility = View.GONE
            textIntermediaryBalanceRow.visibility = View.GONE
            textNewIntermediaryBalanceRow.visibility = View.GONE
        }
        if (account == null) {
            textBalanceRow.visibility = View.GONE
            textNewBalanceRow.visibility = View.GONE
            return
        }
        runInBackground {
            val bal = viewModel.getBalance(account.id)
            runOnUiThread { applyLoadedBalance(account, instrument, intermediaryInstrument, bal) }
        }
    }

    private fun applyLoadedBalance(
        account: Account,
        instrument: Instrument?,
        intermediaryInstrument: Instrument?,
        bal: AccountBalance?
    ) {
        val balance = bal?.balance ?: 0
        textBalance.text = context.getString(
            R.string.label_balance_ar,
            TransactionDisplay.formatAmount(balance)
        )
        textBalanceRow.visibility = View.VISIBLE
        currentAccountType = account.type
        currentBalance = balance

        if (instrument != null) {
            val instrBal = bal?.instrumentBalance ?: 0L
            textInstrumentBalance.text = "Balance: ${
                TransactionDisplay.formatInstrumentAmount(instrBal, instrument)
            }"
            textInstrumentBalanceRow.visibility = View.VISIBLE
            currentInstrumentBalance = instrBal
            currentInstrument = instrument
            updateNewInstrumentBalance()
        }

        if (intermediaryInstrument != null) {
            val interBal = bal?.intermediaryBalance ?: 0L
            textIntermediaryBalance.text = "Balance: ${
                TransactionDisplay.formatInstrumentAmount(interBal, intermediaryInstrument)
            }"
            textIntermediaryBalanceRow.visibility = View.VISIBLE
            currentIntermediaryBalance = interBal
            currentIntermediaryInstrument = intermediaryInstrument
            updateNewIntermediaryBalance()
        }

        updateNewBalance()
    }

    private fun clearAccountDependentState() {
        editInstrumentDebit.text = null
        editInstrumentCredit.text = null
        instrumentRow.visibility = View.GONE
        editIntermediaryDebit.text = null
        editIntermediaryCredit.text = null
        intermediaryRow.visibility = View.GONE
        textBalanceRow.visibility = View.GONE
        textInstrumentBalanceRow.visibility = View.GONE
        textIntermediaryBalanceRow.visibility = View.GONE
        textNewBalanceRow.visibility = View.GONE
        textNewInstrumentBalanceRow.visibility = View.GONE
        textNewIntermediaryBalanceRow.visibility = View.GONE
    }

    private fun updateNewBalance() {
        if (currentAccountType.isEmpty()) {
            textNewBalanceRow.visibility = View.GONE
            return
        }
        val newBalance = BalanceCalculator.project(
            accountType = currentAccountType,
            currentBalance = currentBalance,
            debit = parseAmount(editDebit),
            credit = parseAmount(editCredit)
        )
        textNewBalance.text = context.getString(
            R.string.label_new_balance_ar,
            TransactionDisplay.formatAmount(newBalance)
        )
        textNewBalanceRow.visibility = View.VISIBLE
    }

    private fun updateNewInstrumentBalance() {
        val instrument = currentInstrument
        if (instrument == null || currentAccountType.isEmpty()) {
            textNewInstrumentBalanceRow.visibility = View.GONE
            return
        }
        val factor = 10.0.pow(instrument.decimalPlaces)
        val debit = editInstrumentDebit.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val credit = editInstrumentCredit.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val newBalance = BalanceCalculator.project(
            accountType = currentAccountType,
            currentBalance = currentInstrumentBalance,
            debit = debit,
            credit = credit
        )
        textNewInstrumentBalance.text =
            "New balance: ${TransactionDisplay.formatInstrumentAmount(newBalance, instrument)}"
        textNewInstrumentBalanceRow.visibility = View.VISIBLE
    }

    private fun updateNewIntermediaryBalance() {
        val instrument = currentIntermediaryInstrument
        if (instrument == null || currentAccountType.isEmpty()) {
            textNewIntermediaryBalanceRow.visibility = View.GONE
            return
        }
        val factor = 10.0.pow(instrument.decimalPlaces)
        val debit = editIntermediaryDebit.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val credit = editIntermediaryCredit.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val newBalance = BalanceCalculator.project(
            accountType = currentAccountType,
            currentBalance = currentIntermediaryBalance,
            debit = debit,
            credit = credit
        )
        textNewIntermediaryBalance.text =
            "New balance: ${TransactionDisplay.formatInstrumentAmount(newBalance, instrument)}"
        textNewIntermediaryBalanceRow.visibility = View.VISIBLE
    }

    /** The row's amounts as summed into the running balance-summary line, ignoring validity. */
    fun summaryEntry(): TransactionValidator.EntryData {
        val debit = parseAmount(editDebit)
        val credit = parseAmount(editCredit)
        return TransactionValidator.EntryData(
            accountId = "",
            debitAmount = if (debit != 0L) debit else null,
            creditAmount = if (credit != 0L) credit else null
        )
    }

    /** This row's validated data, or the reason it can't be collected yet. */
    fun toEntryData(): EntryResult {
        val accountPos = spinner.selectedItemPosition
        if (accountPos <= 0 || accountPos > accounts.size) return EntryResult.Incomplete
        val account = accounts[accountPos - 1]
        val instrument = account.instrumentCode?.let { instrumentsMap[it] }
        val debit = editDebit.text.toString().trim().toLongOrNull()
        val credit = editCredit.text.toString().trim().toLongOrNull()

        var instrumentDebit: Long? = null
        var instrumentCredit: Long? = null
        var intermediaryDebit: Long? = null
        var intermediaryCredit: Long? = null

        if (instrument != null) {
            val parsedDebit = editInstrumentDebit.text.toString().trim().toDoubleOrNull()
            val parsedCredit = editInstrumentCredit.text.toString().trim().toDoubleOrNull()
            if (parsedDebit == null && parsedCredit == null) {
                return EntryResult.InstrumentAmountRequired(instrument.code)
            }
            val factor = 10.0.pow(instrument.decimalPlaces)
            instrumentDebit = parsedDebit?.let { (it * factor).roundToLong() }
            instrumentCredit = parsedCredit?.let { (it * factor).roundToLong() }

            val intermediaryInstrument =
                account.intermediaryInstrumentCode?.let { instrumentsMap[it] }
            if (intermediaryInstrument != null) {
                val interFactor = 10.0.pow(intermediaryInstrument.decimalPlaces)
                intermediaryDebit = editIntermediaryDebit.text.toString().trim()
                    .toDoubleOrNull()?.let { (it * interFactor).roundToLong() }
                intermediaryCredit = editIntermediaryCredit.text.toString().trim()
                    .toDoubleOrNull()?.let { (it * interFactor).roundToLong() }
            }
        }

        return EntryResult.Success(
            TransactionValidator.EntryData(
                accountId = account.id,
                debitAmount = debit,
                creditAmount = credit,
                instrumentDebitAmount = instrumentDebit,
                instrumentCreditAmount = instrumentCredit,
                intermediaryDebitAmount = intermediaryDebit,
                intermediaryCreditAmount = intermediaryCredit
            )
        )
    }

    /** Resets this row back to its just-added, empty state. */
    fun clear() {
        editDebit.text = null
        editCredit.text = null
        spinner.setSelection(0)
    }

    /** Whether the user has typed or selected anything in this row. */
    fun hasContent(): Boolean =
        spinner.selectedItemPosition > 0 ||
                editDebit.text.toString().trim().isNotEmpty() ||
                editCredit.text.toString().trim().isNotEmpty() ||
                editInstrumentDebit.text.toString().trim().isNotEmpty() ||
                editInstrumentCredit.text.toString().trim().isNotEmpty() ||
                editIntermediaryDebit.text.toString().trim().isNotEmpty() ||
                editIntermediaryCredit.text.toString().trim().isNotEmpty()

    companion object {
        fun parseAmount(edit: EditText): Long =
            edit.text.toString().trim().replace(",", "").toLongOrNull() ?: 0L
    }
}
