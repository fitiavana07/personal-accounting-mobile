package dev.fitiavana.accounting.ui.transactions

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayout
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.BalanceCalculator
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.ui.common.UiUtils
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import kotlin.math.pow
import kotlin.math.roundToLong
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddTransactionActivity : AppCompatActivity() {

    /** Data-entry mode selected by the tabs; only the inputs differ, not the saved transaction. */
    private enum class Mode { CLASSIC, SIMPLE_TRANSFER }

    private var mode = Mode.CLASSIC

    private lateinit var viewModel: AddTransactionViewModel
    private lateinit var accounts: List<Account>
    private lateinit var instrumentsMap: Map<String, Instrument>

    private val dateFormat =
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private var selectedCalendar = Calendar.getInstance()

    private lateinit var textDatetime: TextView
    private lateinit var editNote: EditText
    private lateinit var entriesContainer: LinearLayout
    private lateinit var textBalanceSummary: TextView
    private lateinit var modeClassic: View
    private lateinit var modeSimpleTransfer: View
    private lateinit var editTransferAmount: EditText
    private lateinit var transferFrom: TransferSide
    private lateinit var transferTo: TransferSide

    /** Accounts offered by the Simple Transfer spinners; see [SimpleTransferBuilder]. */
    private var transferAccounts: List<Account> = emptyList()

    /**
     * One side of a Simple Transfer, with its current and projected balance.
     * The From side is credited and the To side debited by the amount entered.
     */
    private class TransferSide(
        val spinner: Spinner,
        val textBalance: TextView,
        val textNewBalance: TextView,
        /** True when the transferred amount is a debit for this side. */
        val isDebit: Boolean,
        var account: Account? = null,
        var balance: Long = 0L
    )

    private data class EntryRow(
        val container: View,
        val spinner: Spinner,
        val editDebit: EditText,
        val editCredit: EditText,
        val btnRemove: ImageButton,
        val editInstrumentDebit: EditText,
        val editInstrumentCredit: EditText,
        val instrumentRow: View,
        val textInstrumentCode: TextView,
        val editIntermediaryDebit: EditText,
        val editIntermediaryCredit: EditText,
        val intermediaryRow: View,
        val textIntermediaryCode: TextView,
        val textBalanceRow: View,
        val textBalance: TextView,
        val textInstrumentBalanceRow: View,
        val textInstrumentBalance: TextView,
        val textIntermediaryBalanceRow: View,
        val textIntermediaryBalance: TextView,
        val textNewBalanceRow: View,
        val textNewBalance: TextView,
        val textNewInstrumentBalanceRow: View,
        val textNewInstrumentBalance: TextView,
        val textNewIntermediaryBalanceRow: View,
        val textNewIntermediaryBalance: TextView,
        var currentBalance: Long = 0L,
        var currentAccountType: String = "",
        var currentInstrumentBalance: Long = 0L,
        var currentInstrument: Instrument? = null,
        var currentIntermediaryBalance: Long = 0L,
        var currentIntermediaryInstrument: Instrument? = null
    )

    private val entryRows = mutableListOf<EntryRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_new_transaction)

        val container = AppContainer.getInstance(this)
        viewModel = ViewModelProvider(
            this,
            AddTransactionViewModelFactory(
                container.transactionRepository,
                container.accountRepository,
                container.balanceRepository,
                container.instrumentRepository
            )
        )
            .get(AddTransactionViewModel::class.java)

        textDatetime = findViewById(R.id.text_datetime)
        editNote = findViewById(R.id.edit_note)
        entriesContainer = findViewById(R.id.entries_container)
        textBalanceSummary = findViewById(R.id.text_balance_summary)
        modeClassic = findViewById(R.id.mode_classic)
        modeSimpleTransfer = findViewById(R.id.mode_simple_transfer)
        editTransferAmount = findViewById(R.id.edit_transfer_amount)
        transferFrom = TransferSide(
            spinner = findViewById(R.id.spinner_transfer_from),
            textBalance = findViewById(R.id.text_transfer_from_balance),
            textNewBalance = findViewById(R.id.text_transfer_from_new_balance),
            isDebit = false
        )
        transferTo = TransferSide(
            spinner = findViewById(R.id.spinner_transfer_to),
            textBalance = findViewById(R.id.text_transfer_to_balance),
            textNewBalance = findViewById(R.id.text_transfer_to_new_balance),
            isDebit = true
        )

        setupModeTabs(findViewById(R.id.tabs_transaction_mode))
        setupTransferAmountInput()
        setupTransferSide(transferFrom)
        setupTransferSide(transferTo)

        updateDatetimeDisplay()

        textDatetime.setOnClickListener { pickDate() }

        Thread {
            val loaded = viewModel.loadAccountsAndInstruments()
            accounts = loaded.accounts
            instrumentsMap = loaded.instrumentsByCode
            runOnUiThread {
                addEntryRow()
                addEntryRow()
                populateTransferSpinners()
                recalculateBalanceSummary()
            }
        }.start()

        findViewById<Button>(R.id.btn_add_entry).setOnClickListener {
            addEntryRow()
            recalculateBalanceSummary()
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveTransaction()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    confirmDiscardAndFinish()
                }
            }
        )
    }

    private fun setupModeTabs(tabLayout: TabLayout) {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_mode_classic))
        tabLayout.addTab(
            tabLayout.newTab().setText(R.string.tab_mode_simple_transfer)
        )
        tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    showMode(
                        if (tab.position == 1) Mode.SIMPLE_TRANSFER
                        else Mode.CLASSIC
                    )
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            }
        )
    }

    private fun showMode(newMode: Mode) {
        mode = newMode
        modeClassic.visibility =
            if (newMode == Mode.CLASSIC) View.VISIBLE else View.GONE
        modeSimpleTransfer.visibility =
            if (newMode == Mode.SIMPLE_TRANSFER) View.VISIBLE else View.GONE
        recalculateBalanceSummary()
    }

    private fun setupTransferAmountInput() {
        editTransferAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun afterTextChanged(s: Editable?) {
                updateTransferNewBalance(transferFrom)
                updateTransferNewBalance(transferTo)
                recalculateBalanceSummary()
            }
        })
    }

    /** Loads and shows the side's balance whenever its account selection changes. */
    private fun setupTransferSide(side: TransferSide) {
        side.spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val account = selectedTransferAccount(side.spinner)
                    side.account = account
                    side.balance = 0L
                    if (account == null) {
                        hideTransferBalances(side)
                        return
                    }
                    Thread {
                        val balance =
                            viewModel.getBalance(account.id)?.balance ?: 0L
                        runOnUiThread {
                            // a newer selection may have won the race
                            if (side.account?.id != account.id) return@runOnUiThread
                            side.balance = balance
                            side.textBalance.text = getString(
                                R.string.label_balance_ar,
                                TransactionDisplay.formatAmount(balance)
                            )
                            side.textBalance.visibility = View.VISIBLE
                            updateTransferNewBalance(side)
                        }
                    }.start()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    side.account = null
                    hideTransferBalances(side)
                }
            }
    }

    private fun hideTransferBalances(side: TransferSide) {
        side.textBalance.visibility = View.GONE
        side.textNewBalance.visibility = View.GONE
    }

    private fun updateTransferNewBalance(side: TransferSide) {
        val account = side.account
        if (account == null) {
            side.textNewBalance.visibility = View.GONE
            return
        }
        val amount = parseAmount(editTransferAmount)
        val newBalance = BalanceCalculator.project(
            accountType = account.type,
            currentBalance = side.balance,
            debit = if (side.isDebit) amount else 0L,
            credit = if (side.isDebit) 0L else amount
        )
        side.textNewBalance.text = getString(
            R.string.label_new_balance_ar,
            TransactionDisplay.formatAmount(newBalance)
        )
        side.textNewBalance.visibility = View.VISIBLE
    }

    private fun populateTransferSpinners() {
        transferAccounts = SimpleTransferBuilder.selectableAccounts(accounts)
        val names = listOf(getString(R.string.spinner_select_account)) +
                transferAccounts.map { it.name }
        listOf(transferFrom, transferTo).forEach { side ->
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                names
            )
            adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
            side.spinner.adapter = adapter
            side.spinner.setSelection(0)
        }
    }

    private fun selectedTransferAccount(spinner: Spinner): Account? {
        val position = spinner.selectedItemPosition
        return if (position > 0 && position <= transferAccounts.size) {
            transferAccounts[position - 1]
        } else {
            null
        }
    }

    private fun updateDatetimeDisplay() {
        textDatetime.text = dateFormat.format(selectedCalendar.time)
    }

    private fun pickDate() {
        val cal = selectedCalendar
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedCalendar.set(Calendar.YEAR, year)
                selectedCalendar.set(Calendar.MONTH, month)
                selectedCalendar.set(Calendar.DAY_OF_MONTH, day)
                pickTime()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickTime() {
        val cal = selectedCalendar
        TimePickerDialog(
            this,
            { _, hour, minute ->
                selectedCalendar.set(Calendar.HOUR_OF_DAY, hour)
                selectedCalendar.set(Calendar.MINUTE, minute)
                selectedCalendar.set(Calendar.SECOND, 0)
                updateDatetimeDisplay()
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun addEntryRow() {
        val row = layoutInflater.inflate(
            R.layout.item_entry_row,
            entriesContainer,
            false
        )
        val spinner = row.findViewById<Spinner>(R.id.spinner_account)
        val editDebit = row.findViewById<EditText>(R.id.edit_debit)
        val editCredit = row.findViewById<EditText>(R.id.edit_credit)
        val btnRemove = row.findViewById<ImageButton>(R.id.btn_remove_entry)
        val editInstrumentDebit =
            row.findViewById<EditText>(R.id.edit_instrument_debit)
        val editInstrumentCredit =
            row.findViewById<EditText>(R.id.edit_instrument_credit)
        val instrumentRow = row.findViewById<View>(R.id.row_instrument_amounts)
        val textInstrumentCode =
            row.findViewById<TextView>(R.id.text_instrument_code)
        val editIntermediaryDebit =
            row.findViewById<EditText>(R.id.edit_intermediary_debit)
        val editIntermediaryCredit =
            row.findViewById<EditText>(R.id.edit_intermediary_credit)
        val intermediaryRow =
            row.findViewById<View>(R.id.row_intermediary_amounts)
        val textIntermediaryCode =
            row.findViewById<TextView>(R.id.text_intermediary_instrument_code)
        val textBalanceRow = row.findViewById<View>(R.id.text_balance_row)
        val textBalance = row.findViewById<TextView>(R.id.text_balance)
        val textInstrumentBalanceRow =
            row.findViewById<View>(R.id.text_instrument_balance_row)
        val textInstrumentBalance =
            row.findViewById<TextView>(R.id.text_instrument_balance)
        val textIntermediaryBalanceRow =
            row.findViewById<View>(R.id.text_intermediary_balance_row)
        val textIntermediaryBalance =
            row.findViewById<TextView>(R.id.text_intermediary_balance)
        val textNewBalanceRow =
            row.findViewById<View>(R.id.text_new_balance_row)
        val textNewBalance = row.findViewById<TextView>(R.id.text_new_balance)
        val textNewInstrumentBalanceRow =
            row.findViewById<View>(R.id.text_new_instrument_balance_row)
        val textNewInstrumentBalance =
            row.findViewById<TextView>(R.id.text_new_instrument_balance)
        val textNewIntermediaryBalanceRow =
            row.findViewById<View>(R.id.text_new_intermediary_balance_row)
        val textNewIntermediaryBalance =
            row.findViewById<TextView>(R.id.text_new_intermediary_balance)

        // holder to let closures below reference entryRow before it is assigned
        val entryRowRef = arrayOfNulls<EntryRow>(1)

        val accountNames =
            listOf(getString(R.string.spinner_select_account)) + accounts.map { it.name }
        val spinnerAdapter = ArrayAdapter(
            this,
            R.layout.item_spinner_small,
            accountNames
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter
        spinner.setSelection(0)

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val account =
                        if (position > 0 && position <= accounts.size) accounts[position - 1] else null
                    val instrument =
                        account?.instrumentCode?.let { instrumentsMap[it] }
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
                    val intermediaryInstrument =
                        account?.intermediaryInstrumentCode?.let { instrumentsMap[it] }
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
                    if (account != null) {
                        Thread {
                            val bal = viewModel.getBalance(account.id)
                            runOnUiThread {
                                val balance = bal?.balance ?: 0
                                textBalance.text = getString(
                                    R.string.label_balance_ar,
                                    TransactionDisplay.formatAmount(balance)
                                )
                                textBalanceRow.visibility = View.VISIBLE
                                entryRowRef[0]?.currentAccountType =
                                    account.type
                                if (instrument != null) {
                                    val instrBal = bal?.instrumentBalance ?: 0L
                                    textInstrumentBalance.text = "Balance: ${
                                        TransactionDisplay.formatInstrumentAmount(
                                            instrBal,
                                            instrument
                                        )
                                    }"
                                    textInstrumentBalanceRow.visibility =
                                        View.VISIBLE
                                    entryRowRef[0]?.currentInstrumentBalance =
                                        instrBal
                                    entryRowRef[0]?.currentInstrument =
                                        instrument
                                    entryRowRef[0]?.let {
                                        updateNewInstrumentBalance(
                                            it
                                        )
                                    }
                                }
                                if (intermediaryInstrument != null) {
                                    val interBal =
                                        bal?.intermediaryBalance ?: 0L
                                    textIntermediaryBalance.text = "Balance: ${
                                        TransactionDisplay.formatInstrumentAmount(
                                            interBal,
                                            intermediaryInstrument
                                        )
                                    }"
                                    textIntermediaryBalanceRow.visibility =
                                        View.VISIBLE
                                    entryRowRef[0]?.currentIntermediaryBalance =
                                        interBal
                                    entryRowRef[0]?.currentIntermediaryInstrument =
                                        intermediaryInstrument
                                    entryRowRef[0]?.let {
                                        updateNewIntermediaryBalance(
                                            it
                                        )
                                    }
                                }
                                entryRowRef[0]?.currentBalance = balance
                                entryRowRef[0]?.currentAccountType =
                                    account.type
                                entryRowRef[0]?.let { updateNewBalance(it) }
                            }
                        }.start()
                    } else {
                        textBalanceRow.visibility = View.GONE
                        textNewBalanceRow.visibility = View.GONE
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
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
            }

        editInstrumentDebit.addTextChangedListener(object : TextWatcher {
            var updating = false
            override fun beforeTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (!updating && !s.isNullOrEmpty()) {
                    updating = true
                    editInstrumentCredit.text = null
                    updating = false
                }
                entryRowRef[0]?.let { updateNewInstrumentBalance(it) }
            }
        })

        editInstrumentCredit.addTextChangedListener(object : TextWatcher {
            var updating = false
            override fun beforeTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (!updating && !s.isNullOrEmpty()) {
                    updating = true
                    editInstrumentDebit.text = null
                    updating = false
                }
                entryRowRef[0]?.let { updateNewInstrumentBalance(it) }
            }
        })

        editIntermediaryDebit.addTextChangedListener(object : TextWatcher {
            var updating = false
            override fun beforeTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (!updating && !s.isNullOrEmpty()) {
                    updating = true
                    editIntermediaryCredit.text = null
                    updating = false
                }
                entryRowRef[0]?.let { updateNewIntermediaryBalance(it) }
            }
        })

        editIntermediaryCredit.addTextChangedListener(object : TextWatcher {
            var updating = false
            override fun beforeTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (!updating && !s.isNullOrEmpty()) {
                    updating = true
                    editIntermediaryDebit.text = null
                    updating = false
                }
                entryRowRef[0]?.let { updateNewIntermediaryBalance(it) }
            }
        })

        editDebit.addTextChangedListener(object : TextWatcher {
            var updating = false
            override fun beforeTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (!updating && !s.isNullOrEmpty()) {
                    updating = true
                    editCredit.text = null
                    updating = false
                }
                entryRowRef[0]?.let { updateNewBalance(it) }
                recalculateBalanceSummary()
            }
        })

        editCredit.addTextChangedListener(object : TextWatcher {
            var updating = false
            override fun beforeTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                st: Int,
                c: Int,
                a: Int
            ) {
            }

            override fun afterTextChanged(s: Editable?) {
                if (!updating && !s.isNullOrEmpty()) {
                    updating = true
                    editDebit.text = null
                    updating = false
                }
                entryRowRef[0]?.let { updateNewBalance(it) }
                recalculateBalanceSummary()
            }
        })

        ImageViewCompat.setImageTintList(
            btnRemove,
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    R.color.icon_remove
                )
            )
        )

        val entryRow = EntryRow(
            row,
            spinner,
            editDebit,
            editCredit,
            btnRemove,
            editInstrumentDebit,
            editInstrumentCredit,
            instrumentRow,
            textInstrumentCode,
            editIntermediaryDebit,
            editIntermediaryCredit,
            intermediaryRow,
            textIntermediaryCode,
            textBalanceRow,
            textBalance,
            textInstrumentBalanceRow,
            textInstrumentBalance,
            textIntermediaryBalanceRow,
            textIntermediaryBalance,
            textNewBalanceRow,
            textNewBalance,
            textNewInstrumentBalanceRow,
            textNewInstrumentBalance,
            textNewIntermediaryBalanceRow,
            textNewIntermediaryBalance
        )
        entryRowRef[0] = entryRow
        entryRows.add(entryRow)
        entriesContainer.addView(row)

        editDebit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && entryRows.size == 2) {
                val text = editDebit.text.toString().trim()
                if (text.isNotEmpty()) {
                    val other = entryRows.first { it !== entryRow }
                    if (other.editCredit.text.isNullOrEmpty()) other.editCredit.setText(
                        text
                    )
                }
            }
        }

        editCredit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && entryRows.size == 2) {
                val text = editCredit.text.toString().trim()
                if (text.isNotEmpty()) {
                    val other = entryRows.first { it !== entryRow }
                    if (other.editDebit.text.isNullOrEmpty()) other.editDebit.setText(
                        text
                    )
                }
            }
        }

        btnRemove.setOnClickListener {
            entriesContainer.removeView(row)
            entryRows.remove(entryRow)
            updateRemoveButtonVisibility()
            recalculateBalanceSummary()
        }

        updateRemoveButtonVisibility()
    }

    private fun parseAmount(edit: EditText): Long =
        edit.text.toString().trim().replace(",", "").toLongOrNull() ?: 0L

    private fun updateNewBalance(entryRow: EntryRow) {
        if (entryRow.currentAccountType.isEmpty()) {
            entryRow.textNewBalanceRow.visibility = View.GONE
            return
        }
        val newBalance = BalanceCalculator.project(
            accountType = entryRow.currentAccountType,
            currentBalance = entryRow.currentBalance,
            debit = parseAmount(entryRow.editDebit),
            credit = parseAmount(entryRow.editCredit)
        )
        entryRow.textNewBalance.text = getString(
            R.string.label_new_balance_ar,
            TransactionDisplay.formatAmount(newBalance)
        )
        entryRow.textNewBalanceRow.visibility = View.VISIBLE
    }

    private fun updateNewInstrumentBalance(entryRow: EntryRow) {
        val instrument = entryRow.currentInstrument
        if (instrument == null || entryRow.currentAccountType.isEmpty()) {
            entryRow.textNewInstrumentBalanceRow.visibility = View.GONE
            return
        }
        val factor = Math.pow(10.0, instrument.decimalPlaces.toDouble())
        val debit = entryRow.editInstrumentDebit.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val credit = entryRow.editInstrumentCredit.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val newBalance = BalanceCalculator.project(
            accountType = entryRow.currentAccountType,
            currentBalance = entryRow.currentInstrumentBalance,
            debit = debit,
            credit = credit
        )
        entryRow.textNewInstrumentBalance.text = "New balance: ${
            TransactionDisplay.formatInstrumentAmount(
                newBalance,
                instrument
            )
        }"
        entryRow.textNewInstrumentBalanceRow.visibility = View.VISIBLE
    }

    private fun updateNewIntermediaryBalance(entryRow: EntryRow) {
        val instrument = entryRow.currentIntermediaryInstrument
        if (instrument == null || entryRow.currentAccountType.isEmpty()) {
            entryRow.textNewIntermediaryBalanceRow.visibility = View.GONE
            return
        }
        val factor = Math.pow(10.0, instrument.decimalPlaces.toDouble())
        val debit = entryRow.editIntermediaryDebit.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val credit = entryRow.editIntermediaryCredit.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val newBalance = BalanceCalculator.project(
            accountType = entryRow.currentAccountType,
            currentBalance = entryRow.currentIntermediaryBalance,
            debit = debit,
            credit = credit
        )
        entryRow.textNewIntermediaryBalance.text = "New balance: ${
            TransactionDisplay.formatInstrumentAmount(
                newBalance,
                instrument
            )
        }"
        entryRow.textNewIntermediaryBalanceRow.visibility = View.VISIBLE
    }

    /**
     * Entries of the active mode as typed so far, for the running totals line.
     * Account ids are irrelevant here — only the amounts are summed.
     */
    private fun summaryEntries(): List<TransactionValidator.EntryData> =
        when (mode) {
            Mode.CLASSIC -> entryRows.map { row ->
                val debit = parseAmount(row.editDebit)
                val credit = parseAmount(row.editCredit)
                TransactionValidator.EntryData(
                    accountId = "",
                    debitAmount = if (debit != 0L) debit else null,
                    creditAmount = if (credit != 0L) credit else null
                )
            }

            Mode.SIMPLE_TRANSFER -> SimpleTransferBuilder.buildEntries(
                fromAccountId = selectedTransferAccount(transferFrom.spinner)?.id
                    ?: "",
                toAccountId = selectedTransferAccount(transferTo.spinner)?.id
                    ?: "",
                amount = parseAmount(editTransferAmount)
            )
        }

    private fun recalculateBalanceSummary() {
        val (totalDebit, totalCredit) =
            TransactionValidator.totals(summaryEntries())
        val totalsText = getString(
            R.string.balance_summary_totals,
            UiUtils.formatAmountAr(this, totalDebit),
            UiUtils.formatAmountAr(this, totalCredit)
        )
        val balanced = totalDebit == totalCredit
        val statusText = if (balanced) {
            getString(R.string.balance_status_balanced)
        } else {
            getString(
                R.string.balance_status_unbalanced,
                UiUtils.formatAmountAr(this, Math.abs(totalDebit - totalCredit))
            )
        }
        textBalanceSummary.text = "$totalsText — $statusText"
        textBalanceSummary.setTextColor(
            ContextCompat.getColor(
                this,
                if (balanced) R.color.gain else R.color.loss
            )
        )
    }

    private fun updateRemoveButtonVisibility() {
        val visible = entryRows.size > 2
        entryRows.forEach {
            it.btnRemove.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    /**
     * Entries for the active mode, or null when the inputs are incomplete — in
     * which case the reason has already been shown to the user.
     */
    private fun collectEntries(): List<TransactionValidator.EntryData>? =
        when (mode) {
            Mode.CLASSIC -> collectClassicEntries()
            Mode.SIMPLE_TRANSFER -> collectTransferEntries()
        }

    private fun collectClassicEntries(): List<TransactionValidator.EntryData>? {
        if (entryRows.isEmpty()) return null

        val entryDataList = mutableListOf<TransactionValidator.EntryData>()

        for (row in entryRows) {
            val accountPos = row.spinner.selectedItemPosition
            if (accountPos <= 0 || accountPos > accounts.size) {
                Toast.makeText(
                    this,
                    getString(R.string.error_validation_entry_incomplete),
                    Toast.LENGTH_SHORT
                ).show()
                return null
            }
            val account = accounts[accountPos - 1]
            val instrument = account.instrumentCode?.let { instrumentsMap[it] }
            val debit = row.editDebit.text.toString().trim().toLongOrNull()
            val credit = row.editCredit.text.toString().trim().toLongOrNull()

            var instrumentDebit: Long? = null
            var instrumentCredit: Long? = null
            var intermediaryDebit: Long? = null
            var intermediaryCredit: Long? = null

            if (instrument != null) {
                val parsedDebit = row.editInstrumentDebit.text.toString().trim()
                    .toDoubleOrNull()
                val parsedCredit =
                    row.editInstrumentCredit.text.toString().trim()
                        .toDoubleOrNull()
                if (parsedDebit == null && parsedCredit == null) {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.error_instrument_amount_required,
                            instrument.code
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return null
                }
                val factor = 10.0.pow(instrument.decimalPlaces)
                instrumentDebit = parsedDebit?.let { (it * factor).roundToLong() }
                instrumentCredit =
                    parsedCredit?.let { (it * factor).roundToLong() }

                val intermediaryInstrument =
                    account.intermediaryInstrumentCode?.let { instrumentsMap[it] }
                if (intermediaryInstrument != null) {
                    val interFactor =
                        10.0.pow(intermediaryInstrument.decimalPlaces)
                    intermediaryDebit =
                        row.editIntermediaryDebit.text.toString().trim()
                            .toDoubleOrNull()
                            ?.let { (it * interFactor).roundToLong() }
                    intermediaryCredit =
                        row.editIntermediaryCredit.text.toString().trim()
                            .toDoubleOrNull()
                            ?.let { (it * interFactor).roundToLong() }
                }
            }

            entryDataList.add(
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

        return entryDataList
    }

    private fun collectTransferEntries(): List<TransactionValidator.EntryData>? {
        val from = selectedTransferAccount(transferFrom.spinner)
        val to = selectedTransferAccount(transferTo.spinner)
        if (from == null || to == null) {
            Toast.makeText(
                this,
                getString(R.string.error_transfer_accounts_required),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        return SimpleTransferBuilder.buildEntries(
            fromAccountId = from.id,
            toAccountId = to.id,
            amount = parseAmount(editTransferAmount)
        )
    }

    private fun saveTransaction() {
        val collectedEntries = collectEntries() ?: return

        when (TransactionValidator.validate(collectedEntries)) {
            TransactionValidator.ValidationResult.Valid -> Unit
            TransactionValidator.ValidationResult.Error.DuplicateAccount -> {
                Toast.makeText(
                    this,
                    getString(R.string.error_duplicate_account),
                    Toast.LENGTH_SHORT
                ).show(); return
            }

            TransactionValidator.ValidationResult.Error.BothFilled -> {
                Toast.makeText(
                    this,
                    getString(R.string.error_entry_both_filled),
                    Toast.LENGTH_SHORT
                ).show(); return
            }

            TransactionValidator.ValidationResult.Error.Incomplete -> {
                Toast.makeText(
                    this,
                    getString(R.string.error_validation_entry_incomplete),
                    Toast.LENGTH_SHORT
                ).show(); return
            }

            TransactionValidator.ValidationResult.Error.Unbalanced -> {
                Toast.makeText(
                    this,
                    getString(R.string.error_validation_balance),
                    Toast.LENGTH_SHORT
                ).show(); return
            }

            TransactionValidator.ValidationResult.Error.MixedDebitCredit -> {
                Toast.makeText(
                    this,
                    getString(R.string.error_mixed_debit_credit),
                    Toast.LENGTH_SHORT
                ).show(); return
            }
        }

        // Entries are stored debits first, whatever order they were entered in.
        val entryDataList = TransactionEntryOrder.debitsFirst(collectedEntries)

        val transactionId = UUID.randomUUID().toString()
        val transaction = Transaction(
            id = transactionId,
            createdAt = System.currentTimeMillis(),
            transactionDatetime = selectedCalendar.timeInMillis,
            note = editNote.text.toString().trim()
        )

        val entries = entryDataList.map { entry ->
            TransactionEntry(
                id = UUID.randomUUID().toString(),
                transactionId = transactionId,
                accountId = entry.accountId,
                debitAmount = entry.debitAmount,
                creditAmount = entry.creditAmount,
                instrumentDebitAmount = entry.instrumentDebitAmount,
                instrumentCreditAmount = entry.instrumentCreditAmount,
                intermediaryDebitAmount = entry.intermediaryDebitAmount,
                intermediaryCreditAmount = entry.intermediaryCreditAmount
            )
        }
        val accountTypesById = accounts.associate { it.id to it.type }

        Thread {
            viewModel.saveTransaction(transaction, entries, accountTypesById)
            runOnUiThread { finish() }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        confirmDiscardAndFinish()
        return true
    }

    private fun hasUnsavedChanges(): Boolean {
        if (editNote.text.toString().trim().isNotEmpty()) return true
        if (editTransferAmount.text.toString().trim().isNotEmpty()) return true
        if (transferFrom.spinner.selectedItemPosition > 0) return true
        if (transferTo.spinner.selectedItemPosition > 0) return true
        return entryRows.any { row ->
            row.spinner.selectedItemPosition > 0 ||
                    row.editDebit.text.toString().trim().isNotEmpty() ||
                    row.editCredit.text.toString().trim().isNotEmpty() ||
                    row.editInstrumentDebit.text.toString().trim()
                        .isNotEmpty() ||
                    row.editInstrumentCredit.text.toString().trim()
                        .isNotEmpty() ||
                    row.editIntermediaryDebit.text.toString().trim()
                        .isNotEmpty() ||
                    row.editIntermediaryCredit.text.toString().trim()
                        .isNotEmpty()
        }
    }

    private fun confirmDiscardAndFinish() {
        if (!hasUnsavedChanges()) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_discard_transaction_title)
            .setMessage(R.string.dialog_discard_transaction_message)
            .setPositiveButton(R.string.action_discard) { _, _ -> finish() }
            .setNegativeButton(R.string.action_keep_editing, null)
            .show()
    }

    companion object {
        fun intent(context: Context) =
            Intent(context, AddTransactionActivity::class.java)
    }
}
