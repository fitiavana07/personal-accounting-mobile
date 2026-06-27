package dev.fitiavana.accounting.ui.addtransaction

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.ImageViewCompat
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.model.Transaction
import dev.fitiavana.accounting.data.model.TransactionEntry
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.BalanceRepository
import dev.fitiavana.accounting.data.repository.TransactionRepository
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.ui.UiUtils
import dev.fitiavana.accounting.ui.transactions.TransactionDisplay
import dev.fitiavana.accounting.ui.transactions.TransactionValidator
import kotlin.math.pow
import kotlin.math.roundToLong
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var transactionRepo: TransactionRepository
    private lateinit var accountRepo: AccountRepository
    private lateinit var accounts: List<Account>
    private lateinit var instrumentsMap: Map<String, Instrument>
    private lateinit var accountBalanceDao: dev.fitiavana.accounting.data.dao.AccountBalanceDao

    private val dateFormat =
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private var selectedCalendar = Calendar.getInstance()

    private lateinit var textDatetime: TextView
    private lateinit var editNote: EditText
    private lateinit var entriesContainer: LinearLayout

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
        var currentBalance: Int = 0,
        var currentAccountType: String = "",
        var currentInstrumentBalance: Long = 0L,
        var currentInstrument: dev.fitiavana.accounting.data.model.Instrument? = null,
        var currentIntermediaryBalance: Long = 0L,
        var currentIntermediaryInstrument: dev.fitiavana.accounting.data.model.Instrument? = null
    )

    private val entryRows = mutableListOf<EntryRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_new_transaction)

        val db = AppDatabase.getInstance(this)
        transactionRepo = TransactionRepository(db.transactionDao())
        accountRepo = AccountRepository(db.accountDao())
        accountBalanceDao = db.accountBalanceDao()

        textDatetime = findViewById(R.id.text_datetime)
        editNote = findViewById(R.id.edit_note)
        entriesContainer = findViewById(R.id.entries_container)

        updateDatetimeDisplay()

        textDatetime.setOnClickListener { pickDate() }

        Thread {
            accounts = db.accountDao().getAllSync()
            instrumentsMap = db.instrumentDao().getAllSync().associateBy { it.code }
            runOnUiThread {
                addEntryRow()
                addEntryRow()
            }
        }.start()

        findViewById<Button>(R.id.btn_add_entry).setOnClickListener {
            addEntryRow()
        }

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveTransaction()
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
        val editInstrumentDebit = row.findViewById<EditText>(R.id.edit_instrument_debit)
        val editInstrumentCredit = row.findViewById<EditText>(R.id.edit_instrument_credit)
        val instrumentRow = row.findViewById<View>(R.id.row_instrument_amounts)
        val textInstrumentCode = row.findViewById<TextView>(R.id.text_instrument_code)
        val editIntermediaryDebit = row.findViewById<EditText>(R.id.edit_intermediary_debit)
        val editIntermediaryCredit = row.findViewById<EditText>(R.id.edit_intermediary_credit)
        val intermediaryRow = row.findViewById<View>(R.id.row_intermediary_amounts)
        val textIntermediaryCode = row.findViewById<TextView>(R.id.text_intermediary_instrument_code)
        val textBalanceRow = row.findViewById<View>(R.id.text_balance_row)
        val textBalance = row.findViewById<TextView>(R.id.text_balance)
        val textInstrumentBalanceRow = row.findViewById<View>(R.id.text_instrument_balance_row)
        val textInstrumentBalance = row.findViewById<TextView>(R.id.text_instrument_balance)
        val textIntermediaryBalanceRow = row.findViewById<View>(R.id.text_intermediary_balance_row)
        val textIntermediaryBalance = row.findViewById<TextView>(R.id.text_intermediary_balance)
        val textNewBalanceRow = row.findViewById<View>(R.id.text_new_balance_row)
        val textNewBalance = row.findViewById<TextView>(R.id.text_new_balance)
        val textNewInstrumentBalanceRow = row.findViewById<View>(R.id.text_new_instrument_balance_row)
        val textNewInstrumentBalance = row.findViewById<TextView>(R.id.text_new_instrument_balance)
        val textNewIntermediaryBalanceRow = row.findViewById<View>(R.id.text_new_intermediary_balance_row)
        val textNewIntermediaryBalance = row.findViewById<TextView>(R.id.text_new_intermediary_balance)

        // holder to let closures below reference entryRow before it is assigned
        val entryRowRef = arrayOfNulls<EntryRow>(1)

        val accountNames = listOf(getString(R.string.spinner_select_account)) + accounts.map { it.name }
        val spinnerAdapter = ArrayAdapter(
            this,
            R.layout.item_spinner_small,
            accountNames
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter
        spinner.setSelection(0)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val account = if (position > 0 && position <= accounts.size) accounts[position - 1] else null
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
                if (account != null) {
                    Thread {
                        val bal = accountBalanceDao.getByAccountId(account.id)
                        runOnUiThread {
                            val balance = bal?.balance ?: 0
                            textBalance.text = "Balance: ${TransactionDisplay.formatAmount(balance)} Ar"
                            textBalanceRow.visibility = View.VISIBLE
                            entryRowRef[0]?.currentAccountType = account.type
                            if (instrument != null) {
                                val instrBal = bal?.instrumentBalance ?: 0L
                                textInstrumentBalance.text = "Balance: ${TransactionDisplay.formatInstrumentAmount(instrBal, instrument)}"
                                textInstrumentBalanceRow.visibility = View.VISIBLE
                                entryRowRef[0]?.currentInstrumentBalance = instrBal
                                entryRowRef[0]?.currentInstrument = instrument
                                entryRowRef[0]?.let { updateNewInstrumentBalance(it) }
                            }
                            if (intermediaryInstrument != null) {
                                val interBal = bal?.intermediaryBalance ?: 0L
                                textIntermediaryBalance.text = "Balance: ${TransactionDisplay.formatInstrumentAmount(interBal, intermediaryInstrument)}"
                                textIntermediaryBalanceRow.visibility = View.VISIBLE
                                entryRowRef[0]?.currentIntermediaryBalance = interBal
                                entryRowRef[0]?.currentIntermediaryInstrument = intermediaryInstrument
                                entryRowRef[0]?.let { updateNewIntermediaryBalance(it) }
                            }
                            entryRowRef[0]?.currentBalance = balance
                            entryRowRef[0]?.currentAccountType = account.type
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
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
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
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
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
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
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
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
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
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!updating && !s.isNullOrEmpty()) {
                    updating = true
                    editCredit.text = null
                    updating = false
                }
                entryRowRef[0]?.let { updateNewBalance(it) }
            }
        })

        editCredit.addTextChangedListener(object : TextWatcher {
            var updating = false
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!updating && !s.isNullOrEmpty()) {
                    updating = true
                    editDebit.text = null
                    updating = false
                }
                entryRowRef[0]?.let { updateNewBalance(it) }
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

        val entryRow = EntryRow(row, spinner, editDebit, editCredit, btnRemove, editInstrumentDebit, editInstrumentCredit, instrumentRow, textInstrumentCode, editIntermediaryDebit, editIntermediaryCredit, intermediaryRow, textIntermediaryCode, textBalanceRow, textBalance, textInstrumentBalanceRow, textInstrumentBalance, textIntermediaryBalanceRow, textIntermediaryBalance, textNewBalanceRow, textNewBalance, textNewInstrumentBalanceRow, textNewInstrumentBalance, textNewIntermediaryBalanceRow, textNewIntermediaryBalance)
        entryRowRef[0] = entryRow
        entryRows.add(entryRow)
        entriesContainer.addView(row)

        editDebit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && entryRows.size == 2) {
                val text = editDebit.text.toString().trim()
                if (text.isNotEmpty()) {
                    val other = entryRows.first { it !== entryRow }
                    if (other.editCredit.text.isNullOrEmpty()) other.editCredit.setText(text)
                }
            }
        }

        editCredit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && entryRows.size == 2) {
                val text = editCredit.text.toString().trim()
                if (text.isNotEmpty()) {
                    val other = entryRows.first { it !== entryRow }
                    if (other.editDebit.text.isNullOrEmpty()) other.editDebit.setText(text)
                }
            }
        }

        btnRemove.setOnClickListener {
            entriesContainer.removeView(row)
            entryRows.remove(entryRow)
            updateRemoveButtonVisibility()
        }

        updateRemoveButtonVisibility()
    }

    private fun updateNewBalance(entryRow: EntryRow) {
        if (entryRow.currentAccountType.isEmpty()) {
            entryRow.textNewBalanceRow.visibility = View.GONE
            return
        }
        val debit = entryRow.editDebit.text.toString().trim().replace(",", "").toIntOrNull() ?: 0
        val credit = entryRow.editCredit.text.toString().trim().replace(",", "").toIntOrNull() ?: 0
        val newBalance = if (entryRow.currentAccountType == "asset" || entryRow.currentAccountType == "expense" || entryRow.currentAccountType == "drawing" || entryRow.currentAccountType == "loss") {
            entryRow.currentBalance + debit - credit
        } else {
            entryRow.currentBalance + credit - debit
        }
        entryRow.textNewBalance.text = "New balance: ${TransactionDisplay.formatAmount(newBalance)} Ar"
        entryRow.textNewBalanceRow.visibility = View.VISIBLE
    }

    private fun updateNewInstrumentBalance(entryRow: EntryRow) {
        val instrument = entryRow.currentInstrument
        if (instrument == null || entryRow.currentAccountType.isEmpty()) {
            entryRow.textNewInstrumentBalanceRow.visibility = View.GONE
            return
        }
        val factor = Math.pow(10.0, instrument.decimalPlaces.toDouble())
        val debit = entryRow.editInstrumentDebit.text.toString().trim().toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val credit = entryRow.editInstrumentCredit.text.toString().trim().toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val newBalance = if (entryRow.currentAccountType == "asset" || entryRow.currentAccountType == "expense" || entryRow.currentAccountType == "drawing" || entryRow.currentAccountType == "loss") {
            entryRow.currentInstrumentBalance + debit - credit
        } else {
            entryRow.currentInstrumentBalance + credit - debit
        }
        entryRow.textNewInstrumentBalance.text = "New balance: ${TransactionDisplay.formatInstrumentAmount(newBalance, instrument)}"
        entryRow.textNewInstrumentBalanceRow.visibility = View.VISIBLE
    }

    private fun updateNewIntermediaryBalance(entryRow: EntryRow) {
        val instrument = entryRow.currentIntermediaryInstrument
        if (instrument == null || entryRow.currentAccountType.isEmpty()) {
            entryRow.textNewIntermediaryBalanceRow.visibility = View.GONE
            return
        }
        val factor = Math.pow(10.0, instrument.decimalPlaces.toDouble())
        val debit = entryRow.editIntermediaryDebit.text.toString().trim().toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val credit = entryRow.editIntermediaryCredit.text.toString().trim().toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: 0L
        val newBalance = if (entryRow.currentAccountType == "asset" || entryRow.currentAccountType == "expense" || entryRow.currentAccountType == "drawing" || entryRow.currentAccountType == "loss") {
            entryRow.currentIntermediaryBalance + debit - credit
        } else {
            entryRow.currentIntermediaryBalance + credit - debit
        }
        entryRow.textNewIntermediaryBalance.text = "New balance: ${TransactionDisplay.formatInstrumentAmount(newBalance, instrument)}"
        entryRow.textNewIntermediaryBalanceRow.visibility = View.VISIBLE
    }

    private fun updateRemoveButtonVisibility() {
        val visible = entryRows.size > 2
        entryRows.forEach {
            it.btnRemove.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun saveTransaction() {
        if (entryRows.isEmpty()) return

        data class InstrumentAmounts(val debit: Long?, val credit: Long?, val interDebit: Long?, val interCredit: Long?)
        val entryDataList = mutableListOf<TransactionValidator.EntryData>()
        val instrumentAmountsList = mutableListOf<InstrumentAmounts>()

        for (row in entryRows) {
            val accountPos = row.spinner.selectedItemPosition
            if (accountPos <= 0 || accountPos > accounts.size) {
                Toast.makeText(
                    this,
                    getString(R.string.error_validation_entry_incomplete),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            val account = accounts[accountPos - 1]
            val instrument = account.instrumentCode?.let { instrumentsMap[it] }
            val debit = row.editDebit.text.toString().trim().toIntOrNull()
            val credit = row.editCredit.text.toString().trim().toIntOrNull()

            if (instrument != null) {
                val rawDebit = row.editInstrumentDebit.text.toString().trim()
                val rawCredit = row.editInstrumentCredit.text.toString().trim()
                val parsedDebit = rawDebit.toDoubleOrNull()
                val parsedCredit = rawCredit.toDoubleOrNull()
                if (parsedDebit == null && parsedCredit == null) {
                    Toast.makeText(
                        this,
                        getString(R.string.error_instrument_amount_required, instrument.code),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                val factor = 10.0.pow(instrument.decimalPlaces)
                val intermediaryInstrument = account.intermediaryInstrumentCode?.let { instrumentsMap[it] }
                val interFactor = intermediaryInstrument?.let { 10.0.pow(it.decimalPlaces) }
                val rawInterDebit = row.editIntermediaryDebit.text.toString().trim()
                val rawInterCredit = row.editIntermediaryCredit.text.toString().trim()
                instrumentAmountsList.add(InstrumentAmounts(
                    debit = parsedDebit?.let { (it * factor).roundToLong() },
                    credit = parsedCredit?.let { (it * factor).roundToLong() },
                    interDebit = if (interFactor != null) rawInterDebit.toDoubleOrNull()?.let { (it * interFactor).roundToLong() } else null,
                    interCredit = if (interFactor != null) rawInterCredit.toDoubleOrNull()?.let { (it * interFactor).roundToLong() } else null
                ))
            } else {
                instrumentAmountsList.add(InstrumentAmounts(null, null, null, null))
            }

            entryDataList.add(TransactionValidator.EntryData(account.id, debit, credit))
        }

        when (TransactionValidator.validate(entryDataList)) {
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
        }

        val transactionId = UUID.randomUUID().toString()
        val transaction = Transaction(
            id = transactionId,
            createdAt = System.currentTimeMillis(),
            transactionDatetime = selectedCalendar.timeInMillis,
            note = editNote.text.toString().trim()
        )

        Thread {
            transactionRepo.insert(transaction)
            entryDataList.forEachIndexed { i, entry ->
                val ia = instrumentAmountsList[i]
                transactionRepo.insertEntry(
                    TransactionEntry(
                        id = UUID.randomUUID().toString(),
                        transactionId = transactionId,
                        accountId = entry.accountId,
                        debitAmount = entry.debitAmount,
                        creditAmount = entry.creditAmount,
                        instrumentDebitAmount = ia.debit,
                        instrumentCreditAmount = ia.credit,
                        intermediaryDebitAmount = ia.interDebit,
                        intermediaryCreditAmount = ia.interCredit
                    )
                )
            }
            val balanceRepo = BalanceRepository(
                accountRepo.dao,
                AppDatabase.getInstance(this).accountBalanceDao(),
                AppDatabase.getInstance(this).transactionDao()
            )
            for (entry in entryDataList) {
                val account = accounts.first { it.id == entry.accountId }
                balanceRepo.recalculateForAccount(entry.accountId, account.type)
            }
            runOnUiThread { finish() }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun intent(context: Context) =
            Intent(context, AddTransactionActivity::class.java)
    }
}
