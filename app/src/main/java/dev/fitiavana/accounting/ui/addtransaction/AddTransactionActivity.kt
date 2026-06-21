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
        val textIntermediaryCode: TextView
    )

    private val entryRows = mutableListOf<EntryRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_new_transaction)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
                val statusBar =
                    insets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.setPadding(0, statusBar.top, 0, 0)
                insets
            }
        }

        val db = AppDatabase.getInstance(this)
        transactionRepo = TransactionRepository(db.transactionDao())
        accountRepo = AccountRepository(db.accountDao())

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

        val accountNames = listOf(getString(R.string.spinner_select_account)) + accounts.map { it.name }
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
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
                }
                val intermediaryInstrument = account?.intermediaryInstrumentCode?.let { instrumentsMap[it] }
                if (intermediaryInstrument != null) {
                    textIntermediaryCode.text = intermediaryInstrument.code
                    intermediaryRow.visibility = View.VISIBLE
                } else {
                    editIntermediaryDebit.text = null
                    editIntermediaryCredit.text = null
                    intermediaryRow.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                editInstrumentDebit.text = null
                editInstrumentCredit.text = null
                instrumentRow.visibility = View.GONE
                editIntermediaryDebit.text = null
                editIntermediaryCredit.text = null
                intermediaryRow.visibility = View.GONE
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

        val entryRow = EntryRow(row, spinner, editDebit, editCredit, btnRemove, editInstrumentDebit, editInstrumentCredit, instrumentRow, textInstrumentCode, editIntermediaryDebit, editIntermediaryCredit, intermediaryRow, textIntermediaryCode)
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
            creationTimestamp = System.currentTimeMillis(),
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
