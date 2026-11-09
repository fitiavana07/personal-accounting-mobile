package dev.fitiavana.accounting.ui.bankaccounts

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils
import java.util.Calendar

/** Adds a credit or a debit to one bank account: a positive amount, a direction, an optional note and a date. */
class AddBankAccountTransactionActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ACCOUNT_ID = "account_id"
        private const val STATE_DATETIME = "datetime"

        fun intent(context: Context, accountId: String): Intent =
            Intent(context, AddBankAccountTransactionActivity::class.java).putExtra(EXTRA_ACCOUNT_ID, accountId)
    }

    private val selectedCalendar: Calendar = Calendar.getInstance()
    private lateinit var datetimeView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)
        if (accountId == null) {
            finish()
            return
        }
        setContentView(R.layout.activity_add_bank_account_transaction)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_new_bank_account_transaction)

        val viewModel = ViewModelProvider(
            this,
            AddBankAccountTransactionViewModelFactory(AppContainer.getInstance(this).bankAccountRepository, accountId)
        ).get(AddBankAccountTransactionViewModel::class.java)

        savedInstanceState?.getLong(STATE_DATETIME)?.takeIf { it > 0 }?.let { selectedCalendar.timeInMillis = it }
        selectedCalendar.set(Calendar.SECOND, 0)
        selectedCalendar.set(Calendar.MILLISECOND, 0)

        datetimeView = findViewById(R.id.text_bank_datetime)
        updateDatetimeDisplay()
        datetimeView.setOnClickListener { pickDate() }

        val directionGroup = findViewById<RadioGroup>(R.id.group_bank_direction)
        val amountLayout = findViewById<TextInputLayout>(R.id.layout_bank_amount)
        val amountInput = findViewById<TextInputEditText>(R.id.input_bank_amount)
        val noteInput = findViewById<TextInputEditText>(R.id.input_bank_note)
        val save = findViewById<MaterialButton>(R.id.button_save_bank_transaction)

        save.setOnClickListener {
            amountLayout.error = null
            val direction =
                if (directionGroup.checkedRadioButtonId == R.id.radio_bank_debit) Direction.DEBIT else Direction.CREDIT
            save.isEnabled = false
            viewModel.save(
                direction, amountInput.text.toString(), noteInput.text.toString(), selectedCalendar.timeInMillis
            ) { error ->
                save.isEnabled = true
                if (error == null) finish()
                else amountLayout.error = getString(R.string.error_bank_transaction_amount)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_DATETIME, selectedCalendar.timeInMillis)
    }

    private fun updateDatetimeDisplay() {
        datetimeView.text = BankAccountDisplay.formatDate(selectedCalendar.timeInMillis)
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
