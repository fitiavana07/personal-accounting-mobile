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
import dev.fitiavana.accounting.ui.common.TransactionDisplay

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
    toSpinner: Spinner,
    toTextBalance: TextView,
    toTextNewBalance: TextView,
    private val editTransferAmount: EditText,
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
        var balance: Long = 0L
    )

    private val from = Side(fromSpinner, fromTextBalance, fromTextNewBalance, isDebit = false)
    private val to = Side(toSpinner, toTextBalance, toTextNewBalance, isDebit = true)

    /** Accounts offered by the spinners; see [SimpleTransferBuilder]. */
    private var transferAccounts: List<Account> = emptyList()

    init {
        editTransferAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNewBalance(from)
                updateNewBalance(to)
                onChanged()
            }
        })
        setupSide(from)
        setupSide(to)
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
                val account = selectedAccount(side.spinner)
                side.account = account
                side.balance = 0L
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
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                side.account = null
                hideBalances(side)
            }
        }
    }

    private fun hideBalances(side: Side) {
        side.textBalance.visibility = View.GONE
        side.textNewBalance.visibility = View.GONE
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

    fun populateSpinners(accounts: List<Account>) {
        transferAccounts = SimpleTransferBuilder.selectableAccounts(accounts)
        val names = listOf(context.getString(R.string.spinner_select_account)) +
                transferAccounts.map { it.name }
        listOf(from, to).forEach { side ->
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            side.spinner.adapter = adapter
            side.spinner.setSelection(0)
        }
    }

    private fun selectedAccount(spinner: Spinner): Account? {
        val position = spinner.selectedItemPosition
        return if (position > 0 && position <= transferAccounts.size) {
            transferAccounts[position - 1]
        } else {
            null
        }
    }

    /** The two entries for this transfer, or null with a Toast already shown if incomplete. */
    fun collectEntries(): List<TransactionValidator.EntryData>? {
        val fromAccount = selectedAccount(from.spinner)
        val toAccount = selectedAccount(to.spinner)
        if (fromAccount == null || toAccount == null) {
            Toast.makeText(
                context,
                context.getString(R.string.error_transfer_accounts_required),
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
            fromAccountId = selectedAccount(from.spinner)?.id ?: "",
            toAccountId = selectedAccount(to.spinner)?.id ?: "",
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
