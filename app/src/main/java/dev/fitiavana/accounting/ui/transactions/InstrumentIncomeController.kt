package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.BalanceCalculator
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Owns the Instrument Income mode's asset and revenue account spinners and
 * shared new-balance field: wires account selection (independent spinners,
 * no chaining between them), loads and previews each side's balance (the
 * asset side has both base and instrument lines; the revenue side, holding
 * no instrument, has a plain base-currency line only), and builds the
 * two-entry income once both accounts are chosen and a new balance above
 * the asset's current instrument balance is typed — the transaction amount
 * is inferred as the difference between the two. See
 * [InstrumentIncomeBuilder] for the pure logic behind account filtering and
 * entry construction, and [InstrumentValueCalculator] for the base-amount
 * rate math (shared with Instrument Transfer).
 */
class InstrumentIncomeController(
    private val context: Context,
    private val viewModel: AddTransactionViewModel,
    private val instrumentsMap: Map<String, Instrument>,
    assetSpinner: Spinner,
    assetTextBalance: TextView,
    assetTextNewBalance: TextView,
    revenueSpinner: Spinner,
    revenueTextBalance: TextView,
    revenueTextNewBalance: TextView,
    private val textAmountCode: TextView,
    private val editIncomeAmount: EditText,
    private val textAmountBase: TextView,
    private val onChanged: () -> Unit,
    private val runInBackground: (() -> Unit) -> Unit,
    private val runOnUiThread: (() -> Unit) -> Unit
) {

    /**
     * One side of an income entry, with its current and projected balances.
     * The asset side is debited and the revenue side credited by the amount
     * entered.
     */
    private class Side(
        val spinner: Spinner,
        val textBalance: TextView,
        val textNewBalance: TextView,
        val isDebit: Boolean,
        var accounts: List<Account> = emptyList(),
        var account: Account? = null,
        var balance: Long = 0L,
        var instrumentBalance: Long = 0L
    )

    private val asset = Side(assetSpinner, assetTextBalance, assetTextNewBalance, isDebit = true)
    private val revenue = Side(revenueSpinner, revenueTextBalance, revenueTextNewBalance, isDebit = false)

    init {
        editIncomeAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNewBalances(asset)
                updateNewBalances(revenue)
                updateAmountBasePreview()
                onChanged()
            }
        })
        setupSide(asset)
        setupSide(revenue)
    }

    private fun setupSide(side: Side) {
        side.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val account = selectedAccount(side)
                side.account = account
                side.balance = 0L
                side.instrumentBalance = 0L
                if (side === asset) {
                    textAmountCode.text = account?.instrumentCode?.let { instrumentsMap[it] }?.code ?: ""
                }
                if (account == null) {
                    hideBalances(side)
                    updateAmountBasePreview()
                    return
                }
                loadBalance(side, account)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                side.account = null
                hideBalances(side)
                if (side === asset) {
                    textAmountCode.text = ""
                }
                updateAmountBasePreview()
            }
        }
    }

    private fun loadBalance(side: Side, account: Account) {
        runInBackground {
            val bal = viewModel.getBalance(account.id)
            runOnUiThread {
                // a newer selection may have won the race
                if (side.account?.id != account.id) return@runOnUiThread
                side.balance = bal?.balance ?: 0L
                side.instrumentBalance = bal?.instrumentBalance ?: 0L
                val instrument = account.instrumentCode?.let { instrumentsMap[it] }
                side.textBalance.text = if (instrument != null) {
                    context.getString(
                        R.string.label_balance_ar_instrument,
                        TransactionDisplay.formatAmount(side.balance),
                        TransactionDisplay.formatInstrumentAmount(side.instrumentBalance, instrument)
                    )
                } else {
                    context.getString(
                        R.string.label_balance_ar,
                        TransactionDisplay.formatAmount(side.balance)
                    )
                }
                side.textBalance.visibility = View.VISIBLE
                updateNewBalances(asset)
                updateNewBalances(revenue)
                updateAmountBasePreview()
            }
        }
    }

    private fun hideBalances(side: Side) {
        side.textBalance.visibility = View.GONE
        side.textNewBalance.visibility = View.GONE
    }

    private fun currentInstrument(): Instrument? =
        asset.account?.instrumentCode?.let { instrumentsMap[it] }

    /**
     * The instrument-denominated new balance typed into the field. An empty
     * or unparseable field defaults to the asset's current instrument
     * balance, i.e. no change yet.
     */
    private fun parsedNewInstrumentBalance(instrument: Instrument): Long {
        val factor = 10.0.pow(instrument.decimalPlaces)
        return editIncomeAmount.text.toString().trim()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: asset.instrumentBalance
    }

    /** The transaction amount, inferred as the typed new balance minus the asset's current balance. */
    private fun transactionInstrumentAmount(instrument: Instrument): Long =
        parsedNewInstrumentBalance(instrument) - asset.instrumentBalance

    private fun computedBaseAmount(instrument: Instrument): Long? =
        InstrumentValueCalculator.computeBaseAmount(
            instrumentAmount = transactionInstrumentAmount(instrument),
            balance = asset.balance,
            instrumentBalance = asset.instrumentBalance
        )

    private fun updateNewBalances(side: Side) {
        val account = side.account
        val instrument = currentInstrument()
        if (account == null || instrument == null) {
            side.textNewBalance.visibility = View.GONE
            return
        }
        val baseAmount = computedBaseAmount(instrument) ?: 0L

        val newBalance = BalanceCalculator.project(
            accountType = account.type,
            currentBalance = side.balance,
            debit = if (side.isDebit) baseAmount else 0L,
            credit = if (side.isDebit) 0L else baseAmount
        )

        if (side === asset) {
            val instrumentAmount = transactionInstrumentAmount(instrument)
            val newInstrumentBalance = BalanceCalculator.project(
                accountType = account.type,
                currentBalance = side.instrumentBalance,
                debit = instrumentAmount,
                credit = 0L
            )
            side.textNewBalance.text = context.getString(
                R.string.label_new_balance_ar_instrument,
                TransactionDisplay.formatAmount(newBalance),
                TransactionDisplay.formatInstrumentAmount(newInstrumentBalance, instrument)
            )
        } else {
            side.textNewBalance.text = context.getString(
                R.string.label_new_balance_ar,
                TransactionDisplay.formatAmount(newBalance)
            )
        }
        side.textNewBalance.visibility = View.VISIBLE
    }

    /**
     * Shows the transaction amount inferred from the typed new balance, in
     * base currency and instrument units — or a warning when the typed new
     * balance isn't above the current one. Hidden while the field is blank
     * (nothing typed yet) or no asset account is selected.
     */
    private fun updateAmountBasePreview() {
        val instrument = currentInstrument()
        if (instrument == null || editIncomeAmount.text.toString().trim().isEmpty()) {
            textAmountBase.visibility = View.GONE
            return
        }
        val instrumentAmount = transactionInstrumentAmount(instrument)
        if (instrumentAmount <= 0L) {
            textAmountBase.text = context.getString(R.string.label_instrument_income_new_balance_too_low)
            textAmountBase.visibility = View.VISIBLE
            return
        }
        val baseAmount = computedBaseAmount(instrument)
        if (baseAmount == null) {
            textAmountBase.visibility = View.GONE
            return
        }
        textAmountBase.text = context.getString(
            R.string.label_amount_plus_ar_instrument,
            TransactionDisplay.formatAmount(baseAmount),
            TransactionDisplay.formatInstrumentAmount(instrumentAmount, instrument)
        )
        textAmountBase.visibility = View.VISIBLE
    }

    fun populateSpinners(accounts: List<Account>) {
        asset.accounts = InstrumentIncomeBuilder.selectableAssetAccounts(accounts)
        populateSpinner(asset)
        revenue.accounts = InstrumentIncomeBuilder.selectableRevenueAccounts(accounts)
        populateSpinner(revenue)
    }

    private fun populateSpinner(side: Side) {
        val names = listOf(context.getString(R.string.spinner_select_account)) +
                side.accounts.map { it.name }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        side.spinner.adapter = adapter
        side.spinner.setSelection(0)
    }

    private fun selectedAccount(side: Side): Account? {
        val position = side.spinner.selectedItemPosition
        return if (position > 0 && position <= side.accounts.size) {
            side.accounts[position - 1]
        } else {
            null
        }
    }

    /** The two entries for this income, or null with a Toast already shown if incomplete. */
    fun collectEntries(): List<TransactionValidator.EntryData>? {
        val assetAccount = asset.account
        val revenueAccount = revenue.account
        if (assetAccount == null || revenueAccount == null) {
            Toast.makeText(
                context,
                context.getString(R.string.error_instrument_income_accounts_required),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        val instrument = currentInstrument()
        val instrumentAmount = instrument?.let { transactionInstrumentAmount(it) } ?: 0L
        if (instrumentAmount < 0L) {
            Toast.makeText(
                context,
                context.getString(R.string.error_instrument_income_new_balance_too_low),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        if (instrumentAmount == 0L) {
            Toast.makeText(
                context,
                context.getString(R.string.error_instrument_income_amount_required),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        val baseAmount = InstrumentValueCalculator.computeBaseAmount(
            instrumentAmount = instrumentAmount,
            balance = asset.balance,
            instrumentBalance = asset.instrumentBalance
        )
        if (baseAmount == null) {
            Toast.makeText(
                context,
                context.getString(R.string.error_instrument_income_no_rate),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        return InstrumentIncomeBuilder.buildEntries(
            assetAccountId = assetAccount.id,
            revenueAccountId = revenueAccount.id,
            instrumentAmount = instrumentAmount,
            baseAmount = baseAmount
        )
    }

    /** Entries as typed so far, for the running totals line — ignores validity. */
    fun summaryEntries(): List<TransactionValidator.EntryData> {
        val instrument = currentInstrument()
        val instrumentAmount = instrument?.let { transactionInstrumentAmount(it) } ?: 0L
        val baseAmount = instrument?.let { computedBaseAmount(it) }
        return InstrumentIncomeBuilder.buildEntries(
            assetAccountId = asset.account?.id ?: "",
            revenueAccountId = revenue.account?.id ?: "",
            instrumentAmount = instrumentAmount,
            baseAmount = baseAmount
        )
    }

    fun hasContent(): Boolean =
        editIncomeAmount.text.toString().trim().isNotEmpty() ||
                asset.spinner.selectedItemPosition > 0 ||
                revenue.spinner.selectedItemPosition > 0
}
