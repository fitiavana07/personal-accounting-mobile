package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.ui.common.TransactionDisplay

/** An account's current balances: base currency, instrument and (when it has one) intermediary instrument. */
data class SideBalance(val balance: Long, val instrumentBalance: Long, val intermediaryBalance: Long = 0L)

fun AccountBalance.toSideBalance() = SideBalance(balance, instrumentBalance, intermediaryBalance)

/**
 * One selectable account side of an instrument-aware transaction: a spinner
 * of eligible accounts, the selected account's current base/instrument
 * balance, and the balance-line preview. Shared by [InstrumentIncomeController]
 * and [InstrumentTransferController] via composition (one instance per
 * side), each of which owns its own new-balance preview and amount formula
 * on top of this.
 */
class AccountSideController(
    private val context: Context,
    private val instrumentsMap: Map<String, Instrument>,
    private val getBalance: (accountId: String) -> SideBalance?,
    val spinner: Spinner,
    val textBalance: TextView,
    val isDebit: Boolean,
    private val onAccountSelected: (Account?) -> Unit,
    private val onBalanceLoaded: () -> Unit,
    private val runInBackground: (() -> Unit) -> Unit,
    private val runOnUiThread: (() -> Unit) -> Unit
) {
    var accounts: List<Account> = emptyList()
        private set
    var account: Account? = null
        private set
    var balance: Long = 0L
        private set
    var instrumentBalance: Long = 0L
        private set
    var intermediaryBalance: Long = 0L
        private set

    init {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selected = selectedAccount()
                account = selected
                resetBalances()
                onAccountSelected(selected)
                if (selected == null) {
                    hideBalance()
                    return
                }
                loadBalance(selected)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                account = null
                hideBalance()
                onAccountSelected(null)
            }
        }
    }

    /** Rebuilds the spinner's adapter from [newAccounts] and resets selection to the placeholder. */
    fun populate(newAccounts: List<Account>) {
        accounts = newAccounts
        val names = listOf(context.getString(R.string.spinner_select_account)) +
                newAccounts.map { it.name }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(0)
    }

    /** Clears the selected account/balances, resets the spinner to the placeholder, and hides the balance text. */
    fun clearSelection() {
        account = null
        resetBalances()
        spinner.setSelection(0)
        hideBalance()
    }

    /** The account currently picked in the spinner, read from the spinner itself so it is never stale. */
    fun selectedAccountId(): String? = selectedAccount()?.id

    /** Picks [accountId] in the spinner; false (selection untouched) when it is not one of the offered accounts. */
    fun select(accountId: String): Boolean {
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index < 0) return false
        spinner.setSelection(index + 1)
        return true
    }

    fun hideBalance() {
        textBalance.visibility = View.GONE
    }

    private fun loadBalance(account: Account) {
        runInBackground {
            val bal = getBalance(account.id)
            runOnUiThread {
                // a newer selection may have won the race
                if (this.account?.id != account.id) return@runOnUiThread
                balance = bal?.balance ?: 0L
                instrumentBalance = bal?.instrumentBalance ?: 0L
                intermediaryBalance = bal?.intermediaryBalance ?: 0L
                val instrument = account.instrumentCode?.let { instrumentsMap[it] }
                val intermediary = account.intermediaryInstrumentCode?.let { instrumentsMap[it] }
                textBalance.text = if (instrument != null && intermediary != null) {
                    context.getString(
                        R.string.label_balance_ar_instrument_intermediary,
                        TransactionDisplay.formatAmount(balance),
                        TransactionDisplay.formatInstrumentAmountNoWrap(instrumentBalance, instrument),
                        TransactionDisplay.formatInstrumentAmountNoWrap(intermediaryBalance, intermediary)
                    )
                } else if (instrument != null) {
                    context.getString(
                        R.string.label_balance_ar_instrument,
                        TransactionDisplay.formatAmount(balance),
                        TransactionDisplay.formatInstrumentAmountNoWrap(instrumentBalance, instrument)
                    )
                } else {
                    context.getString(
                        R.string.label_balance_ar,
                        TransactionDisplay.formatAmount(balance)
                    )
                }
                textBalance.visibility = View.VISIBLE
                onBalanceLoaded()
            }
        }
    }

    private fun resetBalances() {
        balance = 0L
        instrumentBalance = 0L
        intermediaryBalance = 0L
    }

    private fun selectedAccount(): Account? {
        val position = spinner.selectedItemPosition
        return if (position > 0 && position <= accounts.size) accounts[position - 1] else null
    }
}
