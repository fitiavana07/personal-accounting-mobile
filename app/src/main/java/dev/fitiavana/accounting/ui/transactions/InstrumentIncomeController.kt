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
import dev.fitiavana.accounting.features.accounts.takesIncomeOnIntermediary
import dev.fitiavana.accounting.features.balances.BalanceCalculator
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.templates.TemplateSlot
import dev.fitiavana.accounting.features.templates.TemplateSlots
import dev.fitiavana.accounting.features.templates.TemplateWithEntries
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import dev.fitiavana.accounting.ui.common.baseAmountNoWrap
import dev.fitiavana.accounting.ui.common.emphasizeMainAmount
import dev.fitiavana.accounting.ui.common.enableAmountFormatting
import dev.fitiavana.accounting.ui.common.plainText
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
 * [TransferController].
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

    private val getBalance: (String) -> SideBalance? = { id -> viewModel.getBalance(id)?.toSideBalance() }

    private val asset: AccountSideController = AccountSideController(
        context = context,
        instrumentsMap = instrumentsMap,
        getBalance = getBalance,
        spinner = assetSpinner,
        textBalance = assetTextBalance,
        isDebit = true,
        onAccountSelected = { account ->
            textAmountCode.text = account?.let { amountInstrumentOf(it) }?.code ?: ""
            // Default to the asset's pre-associated revenue account, unless one is already chosen.
            account?.linkedRevenueAccountId?.let { if (revenue.account == null) revenue.select(it) }
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
        editIncomeAmount.enableAmountFormatting(allowDecimal = true)
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

    /** Whether [account] takes its income on the intermediary instrument (the flag only counts with an intermediary). */
    private fun incomeOnIntermediary(account: Account?): Boolean = account?.takesIncomeOnIntermediary() == true

    /** The instrument the amount is typed in: the intermediary one for an income-on-intermediary account. */
    private fun amountInstrumentOf(account: Account): Instrument? =
        (if (incomeOnIntermediary(account)) account.intermediaryInstrumentCode else account.instrumentCode)
            ?.let { instrumentsMap[it] }

    private fun currentInstrument(): Instrument? = asset.account?.let { amountInstrumentOf(it) }

    /** The asset's current balance in the instrument the amount is typed in. */
    private fun amountBalance(): Long =
        if (incomeOnIntermediary(asset.account)) asset.intermediaryBalance else asset.instrumentBalance

    /**
     * The new balance typed into the field, in the amount instrument. An empty
     * or unparseable field defaults to the asset's current balance in that
     * instrument, i.e. no change yet.
     */
    private fun parsedNewBalance(instrument: Instrument): Long {
        val factor = 10.0.pow(instrument.decimalPlaces)
        return editIncomeAmount.plainText()
            .toDoubleOrNull()?.let { (it * factor).roundToLong() } ?: amountBalance()
    }

    /** The transaction amount, inferred as the typed new balance minus the asset's current balance. */
    private fun transactionAmount(instrument: Instrument): Long =
        parsedNewBalance(instrument) - amountBalance()

    /** The transaction amount in the asset's own instrument; none when the income is on the intermediary. */
    private fun entryInstrumentAmount(instrument: Instrument): Long? =
        transactionAmount(instrument).takeUnless { incomeOnIntermediary(asset.account) }

    private fun computedBaseAmount(instrument: Instrument): Long? =
        InstrumentValueCalculator.computeBaseAmount(
            instrumentAmount = transactionAmount(instrument),
            balance = asset.balance,
            instrumentBalance = amountBalance()
        )

    private fun intermediaryInstrument(): Instrument? =
        asset.account?.intermediaryInstrumentCode?.let { instrumentsMap[it] }

    /**
     * The intermediary instrument units received: the typed amount itself for
     * an income-on-intermediary account, otherwise derived like the base amount
     * from the asset's own prior ratio (intermediary balance per instrument
     * unit). Null when the asset has no intermediary instrument or no rate.
     */
    private fun computedIntermediaryAmount(instrument: Instrument): Long? {
        if (intermediaryInstrument() == null) return null
        if (incomeOnIntermediary(asset.account)) return transactionAmount(instrument)
        return InstrumentValueCalculator.computeBaseAmount(
            instrumentAmount = transactionAmount(instrument),
            balance = asset.intermediaryBalance,
            instrumentBalance = asset.instrumentBalance
        )
    }

    private fun buildEntries(instrument: Instrument?, baseAmount: Long?) =
        InstrumentIncomeBuilder.buildEntries(
            assetAccountId = asset.account?.id ?: "",
            revenueAccountId = revenue.account?.id ?: "",
            instrumentAmount = instrument?.let { entryInstrumentAmount(it) },
            baseAmount = baseAmount,
            intermediaryAmount = instrument?.let { computedIntermediaryAmount(it) }
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
            val newInstrumentBalance = BalanceCalculator.project(
                accountType = account.type,
                currentBalance = side.instrumentBalance,
                debit = entryInstrumentAmount(instrument) ?: 0L,
                credit = 0L
            )
            val formattedBase = TransactionDisplay.formatAmount(newBalance)
            val formattedInstrument = TransactionDisplay.formatInstrumentAmountNoWrap(
                newInstrumentBalance,
                instrumentsMap[account.instrumentCode] ?: instrument
            )
            val intermediary = intermediaryInstrument()
            var main = formattedInstrument
            val line = if (intermediary != null) {
                val newIntermediaryBalance = BalanceCalculator.project(
                    accountType = account.type,
                    currentBalance = side.intermediaryBalance,
                    debit = computedIntermediaryAmount(instrument) ?: 0L,
                    credit = 0L
                )
                val formattedIntermediary =
                    TransactionDisplay.formatInstrumentAmountNoWrap(newIntermediaryBalance, intermediary)
                if (incomeOnIntermediary(account)) main = formattedIntermediary
                context.getString(
                    R.string.label_new_balance_ar_instrument_intermediary,
                    formattedBase,
                    formattedInstrument,
                    formattedIntermediary
                )
            } else {
                context.getString(R.string.label_new_balance_ar_instrument, formattedBase, formattedInstrument)
            }
            textNewBalance.text = emphasizeMainAmount(context, line, main)
        } else {
            textNewBalance.text = emphasizeMainAmount(
                context,
                context.getString(R.string.label_new_balance_ar, TransactionDisplay.formatAmount(newBalance)),
                baseAmountNoWrap(newBalance)
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
        val amount = transactionAmount(instrument)
        if (amount <= 0L) {
            textAmountBase.text = context.getString(R.string.label_instrument_income_new_balance_too_low)
            textAmountBase.visibility = View.VISIBLE
            return
        }
        val baseAmount = computedBaseAmount(instrument)
        if (baseAmount == null) {
            textAmountBase.visibility = View.GONE
            return
        }
        val formattedBase = TransactionDisplay.formatAmount(baseAmount)
        val formattedInstrument = TransactionDisplay.formatInstrumentAmountNoWrap(amount, instrument)
        val intermediary = intermediaryInstrument()
        val intermediaryAmount = computedIntermediaryAmount(instrument)
        // On an income-on-intermediary account the typed amount already is the intermediary one.
        val line = if (intermediary != null && intermediaryAmount != null && !incomeOnIntermediary(asset.account)) {
            context.getString(
                R.string.label_amount_plus_ar_instrument_intermediary,
                formattedBase,
                formattedInstrument,
                TransactionDisplay.formatInstrumentAmountNoWrap(intermediaryAmount, intermediary)
            )
        } else {
            context.getString(R.string.label_amount_plus_ar_instrument, formattedBase, formattedInstrument)
        }
        textAmountBase.text = emphasizeMainAmount(context, line, formattedInstrument)
        textAmountBase.visibility = View.VISIBLE
    }

    /** Offers the accounts of [InstrumentIncomeBuilder]; the asset side hides those with a zero base balance in [balances]. */
    fun populateSpinners(accounts: List<Account>, balances: Map<String, Long>) {
        asset.populate(InstrumentIncomeBuilder.selectableAssetAccounts(accounts, balances))
        revenue.populate(InstrumentIncomeBuilder.selectableRevenueAccounts(accounts))
    }

    /** The chosen ASSET and REVENUE accounts for saving as a template, or null until both are picked. */
    fun templateSlots(): List<TemplateSlot>? {
        val assetId = asset.selectedAccountId() ?: return null
        val revenueId = revenue.selectedAccountId() ?: return null
        return listOf(TemplateSlot(TemplateSlots.ASSET, assetId), TemplateSlot(TemplateSlots.REVENUE, revenueId))
    }

    /** Selects the template's accounts; false, changing nothing, when one is no longer offered. */
    fun applyTemplate(template: TemplateWithEntries): Boolean {
        val assetId = template.accountFor(TemplateSlots.ASSET) ?: return false
        val revenueId = template.accountFor(TemplateSlots.REVENUE) ?: return false
        if (asset.accounts.none { it.id == assetId } || revenue.accounts.none { it.id == revenueId }) return false
        asset.select(assetId)
        revenue.select(revenueId)
        return true
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
        val instrumentAmount = instrument?.let { transactionAmount(it) } ?: 0L
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
        val baseAmount = instrument?.let { computedBaseAmount(it) }
        if (baseAmount == null) {
            Toast.makeText(
                context,
                context.getString(R.string.error_instrument_income_no_rate),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        return buildEntries(instrument, baseAmount)
    }

    /** Entries as typed so far, for the running totals line — ignores validity. */
    fun summaryEntries(): List<TransactionValidator.EntryData> {
        val instrument = currentInstrument()
        return buildEntries(instrument, instrument?.let { computedBaseAmount(it) })
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
