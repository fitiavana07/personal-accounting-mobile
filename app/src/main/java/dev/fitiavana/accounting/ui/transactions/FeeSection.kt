package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account

/**
 * A transaction fee's inputs, shared by the Swap and Transfer modes: a [toggle] that reveals a [group]
 * holding an [amountField] and an expense account [accountSpinner] ("Select account" first). Starts off
 * with the group hidden; toggling off hides it and clears what was typed or chosen. The amount's unit and
 * formatting stay with the owner, which also listens through [onToggled] and [onAccountChosen].
 */
class FeeSection(
    private val context: Context,
    private val toggle: CompoundButton,
    private val group: View,
    val amountField: EditText,
    private val accountSpinner: Spinner,
    onToggled: () -> Unit,
    onAccountChosen: () -> Unit
) {

    private var accounts: List<Account> = emptyList()

    /** Whether the fee is on, i.e. its inputs are shown and count. */
    var isOn: Boolean
        get() = toggle.isChecked
        set(value) {
            toggle.isChecked = value
        }

    init {
        toggle.isChecked = false
        group.visibility = View.GONE
        toggle.setOnCheckedChangeListener { _, checked ->
            group.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                amountField.text = null
                accountSpinner.setSelection(0)
            }
            onToggled()
        }
        accountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onAccountChosen()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** The id of the chosen expense account, or null while "Select account" is shown. */
    fun selectedAccountId(): String? {
        val position = accountSpinner.selectedItemPosition
        return if (position > 0 && position <= accounts.size) accounts[position - 1].id else null
    }

    /** Offers [accounts] after "Select account", keeping the current choice when it is still offered. */
    fun populate(accounts: List<Account>) {
        val keep = selectedAccountId()
        this.accounts = accounts
        val names = listOf(context.getString(R.string.spinner_select_account)) + accounts.map { it.name }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        accountSpinner.adapter = adapter
        val index = accounts.indexOfFirst { it.id == keep }
        accountSpinner.setSelection(if (index >= 0) index + 1 else 0)
    }
}
