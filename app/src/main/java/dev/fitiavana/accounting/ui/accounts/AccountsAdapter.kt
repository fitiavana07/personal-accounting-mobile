package dev.fitiavana.accounting.ui.accounts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account

class AccountsAdapter(
    private val onItemClick: (Account) -> Unit
) : RecyclerView.Adapter<AccountsAdapter.ViewHolder>() {

    private var items: List<Account> = emptyList()

    fun submitList(list: List<Account>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_account, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameView: TextView =
            view.findViewById(R.id.text_account_name)
        private val typeView: TextView =
            view.findViewById(R.id.text_account_type)

        fun bind(account: Account, onClick: (Account) -> Unit) {
            nameView.text = account.name
            val typeLabel = account.type.replaceFirstChar { it.uppercase() }
            val instrumentDisplay = when {
                account.intermediaryInstrumentCode != null -> "${account.instrumentCode} via ${account.intermediaryInstrumentCode}"
                account.instrumentCode != null -> account.instrumentCode
                else -> null
            }
            typeView.text =
                if (instrumentDisplay != null) "$typeLabel · $instrumentDisplay" else typeLabel
            itemView.setOnClickListener { onClick(account) }
        }
    }
}
