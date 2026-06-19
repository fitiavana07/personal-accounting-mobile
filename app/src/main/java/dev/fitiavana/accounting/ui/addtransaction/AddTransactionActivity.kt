package dev.fitiavana.accounting.ui.addtransaction

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.Transaction
import dev.fitiavana.accounting.data.model.TransactionEntry
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.TransactionRepository
import dev.fitiavana.accounting.db.AppDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var transactionRepo: TransactionRepository
    private lateinit var accountRepo: AccountRepository
    private lateinit var accounts: List<Account>

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private var selectedCalendar = Calendar.getInstance()

    private lateinit var textDatetime: TextView
    private lateinit var editNote: EditText
    private lateinit var entriesContainer: LinearLayout

    private data class EntryRow(
        val container: View,
        val spinner: Spinner,
        val editDebit: EditText,
        val editCredit: EditText,
        val btnRemove: ImageButton
    )

    private val entryRows = mutableListOf<EntryRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_transaction)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_new_transaction)

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
        DatePickerDialog(this, { _, year, month, day ->
            selectedCalendar.set(Calendar.YEAR, year)
            selectedCalendar.set(Calendar.MONTH, month)
            selectedCalendar.set(Calendar.DAY_OF_MONTH, day)
            pickTime()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime() {
        val cal = selectedCalendar
        TimePickerDialog(this, { _, hour, minute ->
            selectedCalendar.set(Calendar.HOUR_OF_DAY, hour)
            selectedCalendar.set(Calendar.MINUTE, minute)
            selectedCalendar.set(Calendar.SECOND, 0)
            updateDatetimeDisplay()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun addEntryRow() {
        val row = layoutInflater.inflate(R.layout.item_entry_row, entriesContainer, false)
        val spinner = row.findViewById<Spinner>(R.id.spinner_account)
        val editDebit = row.findViewById<EditText>(R.id.edit_debit)
        val editCredit = row.findViewById<EditText>(R.id.edit_credit)
        val btnRemove = row.findViewById<ImageButton>(R.id.btn_remove_entry)

        val accountNames = accounts.map { it.name }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, accountNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

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
            }
        })

        val entryRow = EntryRow(row, spinner, editDebit, editCredit, btnRemove)
        entryRows.add(entryRow)
        entriesContainer.addView(row)

        btnRemove.setOnClickListener {
            entriesContainer.removeView(row)
            entryRows.remove(entryRow)
            updateRemoveButtonVisibility()
        }

        updateRemoveButtonVisibility()
    }

    private fun updateRemoveButtonVisibility() {
        val visible = entryRows.size > 2
        entryRows.forEach { it.btnRemove.visibility = if (visible) View.VISIBLE else View.GONE }
    }

    private fun saveTransaction() {
        if (entryRows.isEmpty()) return

        var totalDebit = 0.0
        var totalCredit = 0.0
        val validatedEntries = mutableListOf<Triple<String, Double?, Double?>>()

        for ((index, row) in entryRows.withIndex()) {
            val accountPos = row.spinner.selectedItemPosition
            if (accountPos < 0 || accountPos >= accounts.size) {
                Toast.makeText(this, getString(R.string.error_validation_entry_incomplete), Toast.LENGTH_SHORT).show()
                return
            }
            val accountId = accounts[accountPos].id
            val debitStr = row.editDebit.text.toString().trim()
            val creditStr = row.editCredit.text.toString().trim()

            val debit = debitStr.toDoubleOrNull()
            val credit = creditStr.toDoubleOrNull()

            if (debit != null && credit != null) {
                Toast.makeText(this, getString(R.string.error_entry_both_filled), Toast.LENGTH_SHORT).show()
                return
            }
            if (debit == null && credit == null) {
                Toast.makeText(this, getString(R.string.error_validation_entry_incomplete), Toast.LENGTH_SHORT).show()
                return
            }

            totalDebit += debit ?: 0.0
            totalCredit += credit ?: 0.0
            validatedEntries.add(Triple(accountId, debit, credit))
        }

        val epsilon = 0.001
        if (Math.abs(totalDebit - totalCredit) > epsilon) {
            Toast.makeText(this, getString(R.string.error_validation_balance), Toast.LENGTH_SHORT).show()
            return
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
            for ((accountId, debit, credit) in validatedEntries) {
                transactionRepo.insertEntry(
                    TransactionEntry(
                        id = UUID.randomUUID().toString(),
                        transactionId = transactionId,
                        accountId = accountId,
                        debitAmount = debit,
                        creditAmount = credit
                    )
                )
            }
            runOnUiThread { finish() }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun intent(context: Context) = Intent(context, AddTransactionActivity::class.java)
    }
}
