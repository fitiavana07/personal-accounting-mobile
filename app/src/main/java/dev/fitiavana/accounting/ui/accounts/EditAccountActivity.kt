package dev.fitiavana.accounting.ui.accounts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.AccountTypes
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.ui.common.UiUtils

class EditAccountActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "account_id"

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
    private lateinit var intermediaryInstrumentSpinner: Spinner
    private var accountId: String? = null

    private var instruments: List<Instrument> = emptyList()
    private var isLocked: Boolean = false
    private var instrumentInitiallyUnset: Boolean = true
    private var intermediaryInitiallyUnset: Boolean = true

    private lateinit var container: AppContainer
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_account)

        UiUtils.setupActionBar(this)

        container = AppContainer.getInstance(this)
        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)

        initViewModel()
        bindViews()
        setupTypeSpinner()
        observeInstruments()
        setupInstrumentSpinnerListener()
        loadAccountForEditingIfNeeded()
        setupSaveButton()
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(
            this,
            EditAccountViewModelFactory(
                container.accountRepository,
                container.instrumentRepository,
                container.balanceRepository
            )
        )
            .get(EditAccountViewModel::class.java)
    }

    private fun bindViews() {
        nameInput = findViewById(R.id.input_account_name)
        typeSpinner = findViewById(R.id.spinner_account_type)
        instrumentSpinner = findViewById(R.id.spinner_instrument)
        intermediaryInstrumentSpinner =
            findViewById(R.id.spinner_intermediary_instrument)
        saveButton = findViewById(R.id.button_save)
    }

    private fun setupTypeSpinner() {
        val typeDisplayNames =
            resources.getStringArray(R.array.account_type_display)
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            typeDisplayNames
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = spinnerAdapter
    }

    private fun observeInstruments() {
        viewModel.instruments.observe(this) { list ->
            instruments = list
            val displayNames =
                listOf(getString(R.string.spinner_no_instrument)) + list.map { it.code }

            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                displayNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            instrumentSpinner.adapter = adapter

            val intermediaryAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                displayNames
            )
            intermediaryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            intermediaryInstrumentSpinner.adapter = intermediaryAdapter

            val id = accountId
            if (id != null) {
                Thread {
                    val account = viewModel.getAccount(id)
                    runOnUiThread {
                        if (account != null) {
                            val index =
                                list.indexOfFirst { it.code == account.instrumentCode }
                            instrumentSpinner.setSelection(if (index >= 0) index + 1 else 0)
                            val interIndex =
                                list.indexOfFirst { it.code == account.intermediaryInstrumentCode }
                            intermediaryInstrumentSpinner.setSelection(if (interIndex >= 0) interIndex + 1 else 0)
                            instrumentInitiallyUnset =
                                account.instrumentCode == null
                            intermediaryInitiallyUnset =
                                account.intermediaryInstrumentCode == null
                        }
                        updateIntermediarySpinnerEnabled()
                    }
                }.start()
            } else {
                updateIntermediarySpinnerEnabled()
            }
        }
    }

    private fun setupInstrumentSpinnerListener() {
        instrumentSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (position == 0) {
                        intermediaryInstrumentSpinner.setSelection(0)
                    }
                    updateIntermediarySpinnerEnabled()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                    updateIntermediarySpinnerEnabled()
                }
            }
    }

    private fun loadAccountForEditingIfNeeded() {
        val id = accountId
        if (id == null) {
            title = getString(R.string.title_add_account)
            return
        }

        title = getString(R.string.title_edit_account)
        Thread {
            val account = viewModel.getAccount(id)
            val locked = viewModel.hasTransactions(id)
            runOnUiThread {
                if (account != null) {
                    nameInput.setText(account.name)
                    nameInput.setSelection(account.name.length)
                    val typeIndex = AccountTypes.VALUES.indexOf(account.type)
                        .takeIf { it >= 0 } ?: 0
                    typeSpinner.setSelection(typeIndex)
                }
                isLocked = locked
                typeSpinner.isEnabled = !locked
                instrumentSpinner.isEnabled =
                    !locked || instrumentInitiallyUnset
                updateIntermediarySpinnerEnabled()
            }
        }.start()
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                val selectedType =
                    AccountTypes.VALUES[typeSpinner.selectedItemPosition]
                val instrumentPos = instrumentSpinner.selectedItemPosition
                val selectedInstrumentCode =
                    if (instrumentPos == 0) null else instruments[instrumentPos - 1].code
                val interPos =
                    intermediaryInstrumentSpinner.selectedItemPosition
                val selectedIntermediaryCode =
                    if (interPos == 0 || selectedInstrumentCode == null) null else instruments[interPos - 1].code
                Thread {
                    viewModel.saveAccount(
                        accountId,
                        name,
                        selectedType,
                        selectedInstrumentCode,
                        selectedIntermediaryCode
                    )
                    runOnUiThread { finish() }
                }.start()
            }
        }
    }

    private fun updateIntermediarySpinnerEnabled() {
        val canEdit = !isLocked || intermediaryInitiallyUnset
        intermediaryInstrumentSpinner.isEnabled =
            canEdit && instrumentSpinner.selectedItemPosition > 0
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
                    val hasTransactions =
                        viewModel.hasTransactions(accountId!!)
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
                                        val account =
                                            viewModel.getAccount(accountId!!)
                                        if (account != null) viewModel.deleteAccount(
                                            account
                                        )
                                        runOnUiThread { finish() }
                                    }.start()
                                }
                                .setNegativeButton(
                                    android.R.string.cancel,
                                    null
                                )
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
