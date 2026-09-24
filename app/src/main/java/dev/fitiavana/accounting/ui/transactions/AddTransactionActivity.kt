package dev.fitiavana.accounting.ui.transactions

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.ui.common.UiUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AddTransactionActivity : AppCompatActivity() {

    /** Data-entry mode chosen in step 1; only the inputs differ, not the saved transaction. */
    private enum class Mode { CLASSIC, SIMPLE_TRANSFER, INSTRUMENT_TRANSFER, INSTRUMENT_INCOME }

    /** The two-step wizard flow: pick a mode, then fill in its form. */
    private enum class Step { MODE_SELECTION, TRANSACTION_FORM }

    private var mode = Mode.CLASSIC
    private var step = Step.MODE_SELECTION

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
    private lateinit var rootContainer: ViewGroup
    private lateinit var stepModeSelection: View
    private lateinit var stepTransactionForm: View
    private lateinit var modeClassic: View
    private lateinit var modeSimpleTransfer: View
    private lateinit var modeInstrumentTransfer: View
    private lateinit var modeInstrumentIncome: View
    private lateinit var editTransferAmount: EditText
    private lateinit var transferController: SimpleTransferController
    private lateinit var instrumentTransferController: InstrumentTransferController
    private lateinit var instrumentIncomeController: InstrumentIncomeController

    private val entryRows = mutableListOf<EntryRowController>()

    /** Shared background/UI-thread lambda pair, built once instead of at every controller call site. */
    private val backgroundRunner: (() -> Unit) -> Unit = { Thread(it).start() }
    private val uiThreadRunner: (() -> Unit) -> Unit = { runOnUiThread(it) }

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
        rootContainer = findViewById(R.id.add_transaction_root)
        stepModeSelection = findViewById(R.id.step_mode_selection)
        stepTransactionForm = findViewById(R.id.step_transaction_form)
        modeClassic = findViewById(R.id.mode_classic)
        modeSimpleTransfer = findViewById(R.id.mode_simple_transfer)
        modeInstrumentTransfer = findViewById(R.id.mode_instrument_transfer)
        modeInstrumentIncome = findViewById(R.id.mode_instrument_income)
        editTransferAmount = findViewById(R.id.edit_transfer_amount)
        transferController = SimpleTransferController(
            context = this,
            viewModel = viewModel,
            fromSpinner = findViewById(R.id.spinner_transfer_from),
            fromTextBalance = findViewById(R.id.text_transfer_from_balance),
            fromTextNewBalance = findViewById(R.id.text_transfer_from_new_balance),
            fromTextZeroBalanceError = findViewById(R.id.text_transfer_from_zero_balance),
            toSpinner = findViewById(R.id.spinner_transfer_to),
            toTextBalance = findViewById(R.id.text_transfer_to_balance),
            toTextNewBalance = findViewById(R.id.text_transfer_to_new_balance),
            editTransferAmount = editTransferAmount,
            onChanged = { recalculateBalanceSummary() },
            runInBackground = backgroundRunner,
            runOnUiThread = uiThreadRunner
        )

        setupModeSelection()

        updateDatetimeDisplay()

        textDatetime.setOnClickListener { pickDate() }

        Thread {
            val loaded = viewModel.loadAccountsAndInstruments()
            accounts = loaded.accounts
            instrumentsMap = loaded.instrumentsByCode
            runOnUiThread {
                addEntryRow()
                addEntryRow()
                transferController.populateSpinners(accounts)
                instrumentTransferController = InstrumentTransferController(
                    context = this,
                    viewModel = viewModel,
                    instrumentsMap = instrumentsMap,
                    fromSpinner = findViewById(R.id.spinner_instrument_transfer_from),
                    fromTextBalance = findViewById(R.id.text_instrument_transfer_from_balance),
                    fromTextNewBalance = findViewById(R.id.text_instrument_transfer_from_new_balance),
                    fromTextZeroBalanceError = findViewById(R.id.text_instrument_transfer_from_zero_balance),
                    toSpinner = findViewById(R.id.spinner_instrument_transfer_to),
                    toTextBalance = findViewById(R.id.text_instrument_transfer_to_balance),
                    toTextNewBalance = findViewById(R.id.text_instrument_transfer_to_new_balance),
                    textAmountCode = findViewById(R.id.text_instrument_transfer_amount_code),
                    editTransferAmount = findViewById(R.id.edit_instrument_transfer_amount),
                    textAmountBase = findViewById(R.id.text_instrument_transfer_amount_base),
                    onChanged = { recalculateBalanceSummary() },
                    runInBackground = backgroundRunner,
                    runOnUiThread = uiThreadRunner
                )
                instrumentTransferController.populateSpinners(accounts)
                instrumentIncomeController = InstrumentIncomeController(
                    context = this,
                    viewModel = viewModel,
                    instrumentsMap = instrumentsMap,
                    assetSpinner = findViewById(R.id.spinner_instrument_income_asset),
                    assetTextBalance = findViewById(R.id.text_instrument_income_asset_balance),
                    assetTextNewBalance = findViewById(R.id.text_instrument_income_asset_new_balance),
                    revenueSpinner = findViewById(R.id.spinner_instrument_income_revenue),
                    revenueTextBalance = findViewById(R.id.text_instrument_income_revenue_balance),
                    revenueTextNewBalance = findViewById(R.id.text_instrument_income_revenue_new_balance),
                    textAmountCode = findViewById(R.id.text_instrument_income_amount_code),
                    editIncomeAmount = findViewById(R.id.edit_instrument_income_amount),
                    textAmountBase = findViewById(R.id.text_instrument_income_amount_base),
                    onChanged = { recalculateBalanceSummary() },
                    runInBackground = backgroundRunner,
                    runOnUiThread = uiThreadRunner
                )
                instrumentIncomeController.populateSpinners(accounts)
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
                    handleBackNavigation()
                }
            }
        )
    }

    private fun setupModeSelection() {
        val modeOptions = listOf(
            R.id.mode_option_classic to Mode.CLASSIC,
            R.id.mode_option_simple_transfer to Mode.SIMPLE_TRANSFER,
            R.id.mode_option_instrument_transfer to Mode.INSTRUMENT_TRANSFER,
            R.id.mode_option_instrument_income to Mode.INSTRUMENT_INCOME
        )
        modeOptions.forEach { (viewId, optionMode) ->
            val optionView = findViewById<View>(viewId)
            optionView.setOnTouchListener { view, event -> onModeOptionTouched(view, event) }
            optionView.setOnClickListener { selectMode(optionMode) }
        }
    }

    /**
     * Small press-down/release scale so tapping a mode card feels tactile,
     * on top of the existing ripple. Returns false so the click still fires.
     */
    private fun onModeOptionTouched(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }
        return false
    }

    private fun selectMode(newMode: Mode) {
        showMode(newMode)
        showStep(Step.TRANSACTION_FORM)
    }

    /**
     * Slides the entering step in from one edge while the leaving step slides
     * out the other, instead of a hard visibility swap — mirrors the slide-in
     * transition already used when opening this screen from the FAB.
     */
    private fun showStep(newStep: Step) {
        step = newStep
        val enteringView =
            if (newStep == Step.TRANSACTION_FORM) stepTransactionForm else stepModeSelection
        val leavingView =
            if (newStep == Step.TRANSACTION_FORM) stepModeSelection else stepTransactionForm
        val enteringEdge = if (newStep == Step.TRANSACTION_FORM) Gravity.END else Gravity.START
        val leavingEdge = if (newStep == Step.TRANSACTION_FORM) Gravity.START else Gravity.END

        TransitionManager.beginDelayedTransition(
            rootContainer,
            TransitionSet()
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .addTransition(Slide(enteringEdge).addTarget(enteringView))
                .addTransition(Slide(leavingEdge).addTarget(leavingView))
                .setDuration(220)
        )
        stepModeSelection.visibility =
            if (newStep == Step.MODE_SELECTION) View.VISIBLE else View.GONE
        stepTransactionForm.visibility =
            if (newStep == Step.TRANSACTION_FORM) View.VISIBLE else View.GONE
    }

    private fun showMode(newMode: Mode) {
        mode = newMode
        modeClassic.visibility =
            if (newMode == Mode.CLASSIC) View.VISIBLE else View.GONE
        modeSimpleTransfer.visibility =
            if (newMode == Mode.SIMPLE_TRANSFER) View.VISIBLE else View.GONE
        modeInstrumentTransfer.visibility =
            if (newMode == Mode.INSTRUMENT_TRANSFER) View.VISIBLE else View.GONE
        modeInstrumentIncome.visibility =
            if (newMode == Mode.INSTRUMENT_INCOME) View.VISIBLE else View.GONE
        recalculateBalanceSummary()
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
        val entryRow = EntryRowController(
            context = this,
            layoutInflater = layoutInflater,
            parent = entriesContainer,
            viewModel = viewModel,
            accounts = accounts,
            instrumentsMap = instrumentsMap,
            onChanged = { recalculateBalanceSummary() },
            onRemoveClicked = { removeEntryRow(it) },
            runInBackground = backgroundRunner,
            runOnUiThread = uiThreadRunner
        )
        entryRows.add(entryRow)
        entriesContainer.addView(entryRow.view)

        // The first two rows mirror each other's amount so a simple two-line
        // entry can be typed once instead of twice.
        entryRow.editDebit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && entryRows.size == 2) {
                val text = entryRow.editDebit.text.toString().trim()
                if (text.isNotEmpty()) {
                    val other = entryRows.first { it !== entryRow }
                    if (other.editCredit.text.isNullOrEmpty()) other.editCredit.setText(text)
                }
            }
        }

        entryRow.editCredit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && entryRows.size == 2) {
                val text = entryRow.editCredit.text.toString().trim()
                if (text.isNotEmpty()) {
                    val other = entryRows.first { it !== entryRow }
                    if (other.editDebit.text.isNullOrEmpty()) other.editDebit.setText(text)
                }
            }
        }

        updateRemoveButtonVisibility()
    }

    private fun removeEntryRow(entryRow: EntryRowController) {
        entriesContainer.removeView(entryRow.view)
        entryRows.remove(entryRow)
        updateRemoveButtonVisibility()
        recalculateBalanceSummary()
    }

    /**
     * Entries of the active mode as typed so far, for the running totals line.
     * Account ids are irrelevant here — only the amounts are summed.
     */
    private fun summaryEntries(): List<TransactionValidator.EntryData> =
        when (mode) {
            Mode.CLASSIC -> entryRows.map { it.summaryEntry() }
            Mode.SIMPLE_TRANSFER -> transferController.summaryEntries()
            Mode.INSTRUMENT_TRANSFER ->
                if (::instrumentTransferController.isInitialized) {
                    instrumentTransferController.summaryEntries()
                } else {
                    emptyList()
                }
            Mode.INSTRUMENT_INCOME ->
                if (::instrumentIncomeController.isInitialized) {
                    instrumentIncomeController.summaryEntries()
                } else {
                    emptyList()
                }
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
            Mode.SIMPLE_TRANSFER -> transferController.collectEntries()
            Mode.INSTRUMENT_TRANSFER ->
                if (::instrumentTransferController.isInitialized) {
                    instrumentTransferController.collectEntries()
                } else {
                    null
                }
            Mode.INSTRUMENT_INCOME ->
                if (::instrumentIncomeController.isInitialized) {
                    instrumentIncomeController.collectEntries()
                } else {
                    null
                }
        }

    private fun collectClassicEntries(): List<TransactionValidator.EntryData>? {
        if (entryRows.isEmpty()) return null

        val entryDataList = mutableListOf<TransactionValidator.EntryData>()

        for (row in entryRows) {
            when (val result = row.toEntryData()) {
                is EntryRowController.EntryResult.Success -> entryDataList.add(result.data)

                EntryRowController.EntryResult.Incomplete -> {
                    Toast.makeText(
                        this,
                        getString(R.string.error_validation_entry_incomplete),
                        Toast.LENGTH_SHORT
                    ).show()
                    return null
                }

                is EntryRowController.EntryResult.InstrumentAmountRequired -> {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.error_instrument_amount_required,
                            result.instrumentCode
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    return null
                }
            }
        }

        return entryDataList
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
            runOnUiThread {
                Toast.makeText(this, R.string.transaction_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        handleBackNavigation()
        return true
    }

    private fun hasUnsavedChanges(): Boolean {
        if (editNote.text.toString().trim().isNotEmpty()) return true
        if (transferController.hasContent()) return true
        if (::instrumentTransferController.isInitialized && instrumentTransferController.hasContent()) {
            return true
        }
        if (::instrumentIncomeController.isInitialized && instrumentIncomeController.hasContent()) {
            return true
        }
        return entryRows.any { it.hasContent() }
    }

    /**
     * Step 1 has nothing to lose, so back exits the screen directly. Step 2
     * returns to step 1 instead of exiting, confirming discard first if the
     * user has entered data.
     */
    private fun handleBackNavigation() {
        if (step == Step.MODE_SELECTION) {
            finish()
            return
        }
        if (!hasUnsavedChanges()) {
            returnToModeSelection()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_discard_transaction_title)
            .setMessage(R.string.dialog_discard_transaction_message)
            .setPositiveButton(R.string.action_discard) { _, _ -> returnToModeSelection() }
            .setNegativeButton(R.string.action_keep_editing, null)
            .show()
    }

    private fun returnToModeSelection() {
        editNote.setText("")
        entryRows.forEach { it.clear() }
        transferController.clear()
        if (::instrumentTransferController.isInitialized) instrumentTransferController.clear()
        if (::instrumentIncomeController.isInitialized) instrumentIncomeController.clear()
        showStep(Step.MODE_SELECTION)
    }

    companion object {
        fun intent(context: Context) =
            Intent(context, AddTransactionActivity::class.java)
    }
}
