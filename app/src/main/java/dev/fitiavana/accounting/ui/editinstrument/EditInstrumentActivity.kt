package dev.fitiavana.accounting.ui.editinstrument

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
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.repository.InstrumentRepository
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.ui.UiUtils

class EditInstrumentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INSTRUMENT_CODE = "instrument_code"

        private val TYPE_VALUES = listOf("currency", "cryptocurrency", "stock")

        fun addIntent(context: Context): Intent =
            Intent(context, EditInstrumentActivity::class.java)

        fun editIntent(context: Context, code: String): Intent =
            Intent(context, EditInstrumentActivity::class.java)
                .putExtra(EXTRA_INSTRUMENT_CODE, code)
    }

    private lateinit var viewModel: EditInstrumentViewModel
    private var instrumentCode: String? = null

    private lateinit var codeInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var decimalPlacesInput: EditText
    private lateinit var coingeckoIdInput: EditText


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_instrument)

        UiUtils.setupActionBar(this)

        val db = AppDatabase.getInstance(this)
        val repository =
            InstrumentRepository(db.instrumentDao(), db.accountDao())
        viewModel =
            ViewModelProvider(this, EditInstrumentViewModelFactory(repository))
                .get(EditInstrumentViewModel::class.java)

        codeInput = findViewById(R.id.input_instrument_code)
        noteInput = findViewById(R.id.input_instrument_note)
        typeSpinner = findViewById(R.id.spinner_instrument_type)
        decimalPlacesInput = findViewById(R.id.input_decimal_places)
        coingeckoIdInput = findViewById(R.id.input_coingecko_id)
        val saveButton: Button = findViewById(R.id.button_save)

        val typeDisplayNames =
            resources.getStringArray(R.array.instrument_type_display)
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            typeDisplayNames
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = spinnerAdapter

        instrumentCode = intent.getStringExtra(EXTRA_INSTRUMENT_CODE)

        if (instrumentCode != null) {
            title = getString(R.string.title_edit_instrument)
            codeInput.isEnabled = false
            decimalPlacesInput.isEnabled = false
            Thread {
                val instrument = viewModel.getInstrument(instrumentCode!!)
                runOnUiThread {
                    if (instrument != null) {
                        codeInput.setText(instrument.code)
                        noteInput.setText(instrument.note)
                        noteInput.setSelection(instrument.note.length)
                        val typeIndex = TYPE_VALUES.indexOf(instrument.type)
                            .takeIf { it >= 0 } ?: 0
                        typeSpinner.setSelection(typeIndex)
                        decimalPlacesInput.setText(instrument.decimalPlaces.toString())
                        coingeckoIdInput.setText(instrument.coingeckoId ?: "")
                    }
                }
            }.start()
        } else {
            title = getString(R.string.title_add_instrument)
        }

        saveButton.setOnClickListener {
            val code = codeInput.text.toString().trim()
            val note = noteInput.text.toString().trim()
            val decimalPlaces =
                decimalPlacesInput.text.toString().trim().toIntOrNull() ?: 0
            val selectedType = TYPE_VALUES[typeSpinner.selectedItemPosition]
            val coingeckoId = coingeckoIdInput.text.toString().trim().ifEmpty { null }

            if (code.isNotEmpty()) {
                Thread {
                    val instrumentCode = this.instrumentCode;
                    if (instrumentCode == null) {
                        // we're creating
                        viewModel.saveNewInstrument(
                            code,
                            note,
                            selectedType,
                            decimalPlaces,
                            coingeckoId
                        )
                    } else {
                        //we're updating
                        viewModel.saveInstrument(
                            instrumentCode,
                            note,
                            selectedType,
                            decimalPlaces,
                            coingeckoId
                        )
                    }
                    runOnUiThread { finish() }
                }.start()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (instrumentCode != null) {
            menuInflater.inflate(R.menu.menu_edit_instrument, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_instrument -> {
                Thread {
                    val hasAccounts = viewModel.hasAccounts(instrumentCode!!)
                    val hasIntermediaryAccounts =
                        viewModel.hasIntermediaryAccounts(instrumentCode!!)
                    runOnUiThread {
                        if (hasAccounts || hasIntermediaryAccounts) {
                            val message = when {
                                hasAccounts && hasIntermediaryAccounts -> getString(
                                    R.string.dialog_cannot_delete_instrument_message_both
                                )

                                hasAccounts -> getString(R.string.dialog_cannot_delete_instrument_message)
                                else -> getString(R.string.dialog_cannot_delete_instrument_message_intermediary)
                            }
                            AlertDialog.Builder(this)
                                .setTitle(R.string.dialog_cannot_delete_instrument_title)
                                .setMessage(message)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        } else {
                            AlertDialog.Builder(this)
                                .setTitle(R.string.dialog_delete_instrument_title)
                                .setMessage(R.string.dialog_delete_instrument_message)
                                .setPositiveButton(R.string.action_delete) { _, _ ->
                                    Thread {
                                        val instrument = viewModel.getInstrument(instrumentCode!!)
                                        if (instrument != null) viewModel.deleteInstrument(instrument)
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
