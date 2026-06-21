package dev.fitiavana.accounting.ui.editaccount

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.BalanceRepository
import dev.fitiavana.accounting.data.repository.InstrumentRepository
import dev.fitiavana.accounting.db.AppDatabase

class EditAccountActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "account_id"

        private val TYPE_VALUES = listOf("asset", "liability", "equity", "revenue", "expense")

        fun addIntent(context: Context): Intent =
            Intent(context, EditAccountActivity::class.java)

        fun editIntent(context: Context, accountId: String): Intent =
            Intent(context, EditAccountActivity::class.java)
                .putExtra(EXTRA_ACCOUNT_ID, accountId)
    }

    private lateinit var viewModel: EditAccountViewModel
    private lateinit var nameInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var instrumentSpinner: Spinner
    private var accountId: String? = null

    private var instruments: List<Instrument> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_account)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
                val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.setPadding(0, statusBar.top, 0, 0)
                insets
            }
        }

        val db = AppDatabase.getInstance(this)
        val repository = AccountRepository(db.accountDao())
        val instrumentRepository = InstrumentRepository(db.instrumentDao(), db.accountDao())
        viewModel = ViewModelProvider(this, EditAccountViewModelFactory(repository, instrumentRepository))
            .get(EditAccountViewModel::class.java)

        nameInput = findViewById(R.id.input_account_name)
        typeSpinner = findViewById(R.id.spinner_account_type)
        instrumentSpinner = findViewById(R.id.spinner_instrument)
        val saveButton: Button = findViewById(R.id.button_save)

        val typeDisplayNames = resources.getStringArray(R.array.account_type_display)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeDisplayNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = spinnerAdapter

        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)

        viewModel.instruments.observe(this) { list ->
            instruments = list
            val displayNames = listOf(getString(R.string.spinner_no_instrument)) + list.map { it.code }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            instrumentSpinner.adapter = adapter

            if (accountId != null) {
                Thread {
                    val account = viewModel.getAccount(accountId!!)
                    runOnUiThread {
                        if (account != null) {
                            val index = list.indexOfFirst { it.code == account.instrumentCode }
                            instrumentSpinner.setSelection(if (index >= 0) index + 1 else 0)
                        }
                    }
                }.start()
            }
        }

        if (accountId != null) {
            title = getString(R.string.title_edit_account)
            Thread {
                val account = viewModel.getAccount(accountId!!)
                val balanceRepo = BalanceRepository(db.accountDao(), db.accountBalanceDao(), db.transactionDao())
                val locked = balanceRepo.hasTransactions(accountId!!)
                runOnUiThread {
                    if (account != null) {
                        nameInput.setText(account.name)
                        nameInput.setSelection(account.name.length)
                        val typeIndex = TYPE_VALUES.indexOf(account.type).takeIf { it >= 0 } ?: 0
                        typeSpinner.setSelection(typeIndex)
                    }
                    typeSpinner.isEnabled = !locked
                }
            }.start()
        } else {
            title = getString(R.string.title_add_account)
        }

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                val selectedType = TYPE_VALUES[typeSpinner.selectedItemPosition]
                val instrumentPos = instrumentSpinner.selectedItemPosition
                val selectedInstrumentCode = if (instrumentPos == 0) null else instruments[instrumentPos - 1].code
                Thread {
                    viewModel.saveAccount(accountId, name, selectedType, selectedInstrumentCode)
                    runOnUiThread { finish() }
                }.start()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (accountId != null) {
            menuInflater.inflate(R.menu.menu_edit_account, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_account -> {
                Thread {
                    val db = AppDatabase.getInstance(this)
                    val balanceRepo = BalanceRepository(db.accountDao(), db.accountBalanceDao(), db.transactionDao())
                    val hasTransactions = balanceRepo.hasTransactions(accountId!!)
                    runOnUiThread {
                        if (hasTransactions) {
                            AlertDialog.Builder(this)
                                .setTitle(R.string.dialog_cannot_delete_account_title)
                                .setMessage(R.string.dialog_cannot_delete_account_message)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        } else {
                            AlertDialog.Builder(this)
                                .setTitle(R.string.dialog_delete_account_title)
                                .setMessage(R.string.dialog_delete_account_message)
                                .setPositiveButton(R.string.action_delete) { _, _ ->
                                    Thread {
                                        val account = viewModel.getAccount(accountId!!)
                                        if (account != null) viewModel.deleteAccount(account)
                                        runOnUiThread { finish() }
                                    }.start()
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    }
                }.start()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}