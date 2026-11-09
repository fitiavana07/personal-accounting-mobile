package dev.fitiavana.accounting.ui.bankaccounts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.bankaccounts.BankAccount
import dev.fitiavana.accounting.ui.common.UiUtils

/** One card per bank account: its name, balance and the date of that balance. Tap opens the account. */
class BankAccountsAdapter(
    private val onClick: (BankAccount) -> Unit
) : RecyclerView.Adapter<BankAccountsAdapter.ViewHolder>() {

    private var items: List<BankAccount> = emptyList()

    fun submit(list: List<BankAccount>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_bank_account, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position], onClick)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.text_bank_account_name)
        private val balance: TextView = view.findViewById(R.id.text_bank_account_balance)
        private val date: TextView = view.findViewById(R.id.text_bank_account_date)

        fun bind(account: BankAccount, onClick: (BankAccount) -> Unit) {
            name.text = account.name
            balance.text = UiUtils.formatAmountAr(itemView.context, account.balance)
            date.text = BankAccountDisplay.balanceDate(itemView.context, account.balanceDate)
            itemView.setOnClickListener { onClick(account) }
        }
    }
}
