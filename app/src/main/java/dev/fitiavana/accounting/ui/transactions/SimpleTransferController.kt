package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.BalanceCalculator
import dev.fitiavana.accounting.features.templates.TemplateSlot
import dev.fitiavana.accounting.features.templates.TemplateSlots
import dev.fitiavana.accounting.features.templates.TemplateWithEntries
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import dev.fitiavana.accounting.ui.common.enableAmountFormatting

/**
 * Owns the Simple Transfer mode's two account sides and shared amount field:
 * wires account selection, loads and previews each side's balance, and
 * builds the two-entry transfer once both accounts are chosen.
 */
class SimpleTransferController(
    private val context: Context,
    private val viewModel: AddTransactionViewModel,
    fromSpinner: Spinner,
    fromTextBalance: TextView,
    fromTextNewBalance: TextView,
    private val fromTextZeroBalanceError: TextView,
    toSpinner: Spinner,
    toTextBalance: TextView,
    toTextNewBalance: TextView,
    private val editTransferAmount: EditText,
    buttonAll: Button,
    private val onChanged: () -> Unit,
    private val runInBackground: (() -> Unit) -> Unit,
    private val runOnUiThread: (() -> Unit) -> Unit
) {

    /**
     * One side of a transfer, with its current and projected balance. The
     * From side is credited and the To side debited by the amount entered.
     */
    private class Side(
        val spinner: Spinner,
        val textBalance: TextView,
        val textNewBalance: TextView,
        val isDebit: Boolean,
        var account: Account? = null,
        var balance: Long = 0L,
        /** Accounts offered by [spinner], in spinner order after the placeholder. */
        var options: List<Account> = emptyList()
    )

    private val from = Side(fromSpinner, fromTextBalance, fromTextNewBalance, isDebit = false)
    private val to = Side(toSpinner, toTextBalance, toTextNewBalance, isDebit = true)

    init {
        editTransferAmount.enableAmountFormatting()
        editTransferAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNewBalance(from)
                updateNewBalance(to)
                onChanged()
            }
        })
        buttonAll.setOnClickListener { fillAmountWithFromBalance() }
        setupSide(from)
        setupSide(to)
    }

    /** "ALL": sets the amount to the From account's whole balance, if it has one. */
    private fun fillAmountWithFromBalance() {
        if (from.account == null || from.balance <= 0L) return
        editTransferAmount.setText(from.balance.toString())
    }

    /** Loads and shows the side's balance whenever its account selection changes. */
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
                if (side === from) repopulateTo()
                if (account == null) {
                    hideBalances(side)
                    return
                }
                runInBackground {
                    val balance = viewModel.getBalance(account.id)?.balance ?: 0L
                    runOnUiThread {
                        // a newer selection may have won the race
                        if (side.account?.id != account.id) return@runOnUiThread
                        side.balance = balance
                        side.textBalance.text = context.getString(
                            R.string.label_balance_ar,
                            TransactionDisplay.formatAmount(balance)
                        )
                        side.textBalance.visibility = View.VISIBLE
                        updateNewBalance(side)
                        if (side === from) {
                            fromTextZeroBalanceError.text =
                                context.getString(R.string.error_transfer_from_zero_balance)
                            fromTextZeroBalanceError.visibility =
                                if (balance == 0L) View.VISIBLE else View.GONE
                        }
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                side.account = null
                hideBalances(side)
                if (side === from) repopulateTo()
            }
        }
    }

    private fun hideBalances(side: Side) {
        side.textBalance.visibility = View.GONE
        side.textNewBalance.visibility = View.GONE
        if (side === from) fromTextZeroBalanceError.visibility = View.GONE
    }

    private fun updateNewBalance(side: Side) {
        val account = side.account
        if (account == null) {
            side.textNewBalance.visibility = View.GONE
            return
        }
        val amount = EntryRowController.parseAmount(editTransferAmount)
        val newBalance = BalanceCalculator.project(
            accountType = account.type,
            currentBalance = side.balance,
            debit = if (side.isDebit) amount else 0L,
            credit = if (side.isDebit) 0L else amount
        )
        side.textNewBalance.text = context.getString(
            R.string.label_new_balance_ar,
            TransactionDisplay.formatAmount(newBalance)
        )
        side.textNewBalance.visibility = View.VISIBLE
    }

    /** Offers the accounts of [SimpleTransferBuilder]; From hides those with a zero balance in [balances]. */
    fun populateSpinners(accounts: List<Account>, balances: Map<String, Long>) {
        from.options = SimpleTransferBuilder.selectableFromAccounts(accounts, balances)
        allAccounts = accounts
        showOptions(from, from.options)
        repopulateTo()
    }

    private fun showOptions(side: Side, options: List<Account>, selected: Account? = null) {
        side.options = options
        val names = listOf(context.getString(R.string.spinner_select_account)) + options.map { it.name }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        side.spinner.adapter = adapter
        side.spinner.setSelection(options.indexOfFirst { it.id == selected?.id } + 1)
    }

    /** Offers every To account but the From one, keeping the To selection when it is still offered. */
    private fun repopulateTo() {
        showOptions(
            to,
            SimpleTransferBuilder.selectableToAccounts(allAccounts, selectedAccount(from)),
            selectedAccount(to)
        )
    }

    private var allAccounts: List<Account> = emptyList()

    private fun selectedAccount(side: Side): Account? {
        val position = side.spinner.selectedItemPosition
        return if (position > 0 && position <= side.options.size) {
            side.options[position - 1]
        } else {
            null
        }
    }

    /** The chosen FROM and TO accounts for saving as a template, or null until both are picked. */
    fun templateSlots(): List<TemplateSlot>? {
        val fromAccount = selectedAccount(from) ?: return null
        val toAccount = selectedAccount(to) ?: return null
        return listOf(
            TemplateSlot(TemplateSlots.FROM, fromAccount.id),
            TemplateSlot(TemplateSlots.TO, toAccount.id)
        )
    }

    /** Selects the template's accounts; false, changing nothing, when one is no longer offered. */
    fun applyTemplate(template: TemplateWithEntries): Boolean {
        val fromAccount = from.options.firstOrNull { it.id == template.accountFor(TemplateSlots.FROM) }
        val toOptions = SimpleTransferBuilder.selectableToAccounts(allAccounts, fromAccount)
        val toAccount = toOptions.firstOrNull { it.id == template.accountFor(TemplateSlots.TO) }
        if (fromAccount == null || toAccount == null) return false
        // the From listener may only fire later, so narrow the To list now rather than wait for it
        from.spinner.setSelection(from.options.indexOf(fromAccount) + 1)
        showOptions(to, toOptions, toAccount)
        return true
    }

    /** The two entries for this transfer, or null with a Toast already shown if incomplete. */
    fun collectEntries(): List<TransactionValidator.EntryData>? {
        val fromAccount = selectedAccount(from)
        val toAccount = selectedAccount(to)
        if (fromAccount == null || toAccount == null) {
            Toast.makeText(
                context,
                context.getString(R.string.error_transfer_accounts_required),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        if (from.balance == 0L) {
            Toast.makeText(
                context,
                context.getString(R.string.error_transfer_from_zero_balance),
                Toast.LENGTH_SHORT
            ).show()
            return null
        }
        return SimpleTransferBuilder.buildEntries(
            fromAccountId = fromAccount.id,
            toAccountId = toAccount.id,
            amount = EntryRowController.parseAmount(editTransferAmount)
        )
    }

    /** Entries as typed so far, for the running totals line — ignores validity. */
    fun summaryEntries(): List<TransactionValidator.EntryData> =
        SimpleTransferBuilder.buildEntries(
            fromAccountId = selectedAccount(from)?.id ?: "",
            toAccountId = selectedAccount(to)?.id ?: "",
            amount = EntryRowController.parseAmount(editTransferAmount)
        )

    fun hasContent(): Boolean =
        editTransferAmount.text.toString().trim().isNotEmpty() ||
                from.spinner.selectedItemPosition > 0 ||
                to.spinner.selectedItemPosition > 0

    /** Resets both sides and the amount back to their initial, empty state. */
    fun clear() {
        editTransferAmount.text = null
        from.spinner.setSelection(0)
        to.spinner.setSelection(0)
    }
}
