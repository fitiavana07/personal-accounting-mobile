package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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
 * entry construction, [InstrumentValueCalculator] for the base-amount
 * rate math (shared with Instrument Transfer), and [AccountSideController]
 * for the account-selection/balance-loading plumbing shared with
 * [InstrumentTransferController].
 */
class InstrumentIncomeController(
    private val context: Context,
    private val viewModel: AddTransactionViewModel,
    private val instrumentsMap: Map<String, Instrument>,
    assetSpinner: Spinner,
    assetTextBalance: TextView,
    private val assetTextNewBalance: TextView,
    revenueSpinner: Spinner,
    revenueTextBalance: TextView,
    private val revenueTextNewBalance: TextView,
    private val textAmountCode: TextView,
    private val editIncomeAmount: EditText,
    private val textAmountBase: TextView,
    private val onChanged: () -> Unit,
    runInBackground: (() -> Unit) -> Unit,
    runOnUiThread: (() -> Unit) -> Unit
) {

    private val getBalance: (String) -> Pair<Long, Long>? = { id ->
        viewModel.getBalance(id)?.let { it.balance to it.instrumentBalance }
    }

    private val asset: AccountSideController = AccountSideController(
        context = context,
        instrumentsMap = instrumentsMap,
        getBalance = getBalance,
        spinner = assetSpinner,
        textBalance = assetTextBalance,
        isDebit = true,
        onAccountSelected = { account ->
            textAmountCode.text = account?.instrumentCode?.let { instrumentsMap[it] }?.code ?: ""
            if (account == null) {
                assetTextNewBalance.visibility = View.GONE
                updateAmountBasePreview()
            }
        },
        onBalanceLoaded = {
            updateNewBalances(asset)
            updateNewBalances(revenue)
            updateAmountBasePreview()
        },
        runInBackground = runInBackground,
        runOnUiThread = runOnUiThread
    )

    private val revenue: AccountSideController = AccountSideController(
        context = context,
        instrumentsMap = instrumentsMap,
        getBalance = getBalance,
        spinner = revenueSpinner,
        textBalance = revenueTextBalance,
        isDebit = false,
        onAccountSelected = { account ->
            if (account == null) {
                revenueTextNewBalance.visibility = View.GONE
                updateAmountBasePreview()
            }
        },
        onBalanceLoaded = {
            updateNewBalances(asset)
            updateNewBalances(revenue)
            updateAmountBasePreview()
        },
        runInBackground = runInBackground,
        runOnUiThread = runOnUiThread
    )

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

    private fun updateNewBalances(side: AccountSideController) {
        val account = side.account
        val instrument = currentInstrument()
        val textNewBalance = if (side === asset) assetTextNewBalance else revenueTextNewBalance
        if (account == null || instrument == null) {
            textNewBalance.visibility = View.GONE
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
            textNewBalance.text = context.getString(
                R.string.label_new_balance_ar_instrument,
                TransactionDisplay.formatAmount(newBalance),
                TransactionDisplay.formatInstrumentAmount(newInstrumentBalance, instrument)
            )
        } else {
            textNewBalance.text = context.getString(
                R.string.label_new_balance_ar,
                TransactionDisplay.formatAmount(newBalance)
            )
        }
        textNewBalance.visibility = View.VISIBLE
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
        asset.populate(InstrumentIncomeBuilder.selectableAssetAccounts(accounts))
        revenue.populate(InstrumentIncomeBuilder.selectableRevenueAccounts(accounts))
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

    /** Resets both accounts and the amount back to their initial, empty state. */
    fun clear() {
        editIncomeAmount.text = null
        asset.clearSelection()
        revenue.clearSelection()
    }
}
