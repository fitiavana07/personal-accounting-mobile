package dev.fitiavana.accounting.ui.editinstrument

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
import dev.fitiavana.accounting.data.repository.InstrumentRepository
import dev.fitiavana.accounting.db.AppDatabase

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
    private lateinit var codeInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var typeSpinner: Spinner
    private var instrumentCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_instrument)

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

        val repository = InstrumentRepository(AppDatabase.getInstance(this).instrumentDao())
        viewModel = ViewModelProvider(this, EditInstrumentViewModelFactory(repository))
            .get(EditInstrumentViewModel::class.java)

        codeInput = findViewById(R.id.input_instrument_code)
        noteInput = findViewById(R.id.input_instrument_note)
        typeSpinner = findViewById(R.id.spinner_instrument_type)
        val saveButton: Button = findViewById(R.id.button_save)

        val typeDisplayNames = resources.getStringArray(R.array.instrument_type_display)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeDisplayNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = spinnerAdapter

        instrumentCode = intent.getStringExtra(EXTRA_INSTRUMENT_CODE)

        if (instrumentCode != null) {
            title = getString(R.string.title_edit_instrument)
            codeInput.isEnabled = false
            Thread {
                val instrument = viewModel.getInstrument(instrumentCode!!)
                runOnUiThread {
                    if (instrument != null) {
                        codeInput.setText(instrument.code)
                        noteInput.setText(instrument.note)
                        noteInput.setSelection(instrument.note.length)
                        val typeIndex = TYPE_VALUES.indexOf(instrument.type).takeIf { it >= 0 } ?: 0
                        typeSpinner.setSelection(typeIndex)
                    }
                }
            }.start()
        } else {
            title = getString(R.string.title_add_instrument)
        }

        saveButton.setOnClickListener {
            val code = codeInput.text.toString().trim()
            val note = noteInput.text.toString().trim()
            if (code.isNotEmpty()) {
                val selectedType = TYPE_VALUES[typeSpinner.selectedItemPosition]
                Thread {
                    if (instrumentCode == null) {
                        viewModel.saveNewInstrument(code, note, selectedType)
                    } else {
                        viewModel.saveInstrument(instrumentCode, note, selectedType)
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
