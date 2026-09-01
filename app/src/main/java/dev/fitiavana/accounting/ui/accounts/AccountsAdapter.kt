package dev.fitiavana.accounting.ui.accounts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import dev.fitiavana.accounting.ui.common.UiUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AccountsAdapter(
    private val onItemClick: (Account) -> Unit
) : RecyclerView.Adapter<AccountsAdapter.ViewHolder>() {

    private val dateFormat =
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private var items: List<AccountListItem> = emptyList()

    fun submitList(list: List<AccountListItem>) {
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
        holder.bind(items[position], dateFormat, onItemClick)
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameView: TextView =
            view.findViewById(R.id.text_account_name)
        private val typeView: TextView =
            view.findViewById(R.id.text_account_type)
        private val amountView: TextView =
            view.findViewById(R.id.text_account_balance_amount)
        private val instrumentAmountView: TextView =
            view.findViewById(R.id.text_account_instrument_amount)
        private val intermediaryAmountView: TextView =
            view.findViewById(R.id.text_account_intermediary_amount)
        private val exchangeRateView: TextView =
            view.findViewById(R.id.text_account_exchange_rate)
        private val exchangeRateSecondaryView: TextView =
            view.findViewById(R.id.text_account_exchange_rate_secondary)
        private val updatedAtView: TextView =
            view.findViewById(R.id.text_account_balance_updated_at)

        fun bind(
            item: AccountListItem,
            dateFormat: SimpleDateFormat,
            onClick: (Account) -> Unit
        ) {
            val account = item.account
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

            amountView.text =
                UiUtils.formatAmountAr(amountView.context, item.balance)

            if (item.instrument != null) {
                instrumentAmountView.text = TransactionDisplay.formatInstrumentAmount(
                    item.instrumentBalance,
                    item.instrument
                )
                instrumentAmountView.visibility = View.VISIBLE
            } else {
                instrumentAmountView.visibility = View.GONE
            }

            if (item.instrument != null && item.intermediaryInstrument != null) {
                intermediaryAmountView.text = TransactionDisplay.formatInstrumentAmount(
                    item.intermediaryBalance,
                    item.intermediaryInstrument
                )
                intermediaryAmountView.visibility = View.VISIBLE
            } else {
                intermediaryAmountView.visibility = View.GONE
            }

            var exchangeRate: String? = null
            var exchangeRateSecondary: String? = null
            if (item.instrument != null && item.intermediaryInstrument != null) {
                exchangeRate = TransactionDisplay.formatInstrumentExchangeRate(
                    item.instrumentBalance,
                    item.instrument,
                    item.intermediaryBalance,
                    item.intermediaryInstrument
                )
                exchangeRateSecondary = TransactionDisplay.formatExchangeRate(
                    item.balance,
                    item.intermediaryBalance,
                    item.intermediaryInstrument
                )
            } else if (item.instrument != null) {
                exchangeRate = TransactionDisplay.formatExchangeRate(
                    item.balance,
                    item.instrumentBalance,
                    item.instrument
                )
            }

            if (exchangeRate != null) {
                exchangeRateView.text = exchangeRate
                exchangeRateView.visibility = View.VISIBLE
            } else {
                exchangeRateView.visibility = View.GONE
            }
            if (exchangeRateSecondary != null) {
                exchangeRateSecondaryView.text = exchangeRateSecondary
                exchangeRateSecondaryView.visibility = View.VISIBLE
            } else {
                exchangeRateSecondaryView.visibility = View.GONE
            }

            if (item.updatedAt != null) {
                updatedAtView.text = dateFormat.format(Date(item.updatedAt))
                updatedAtView.visibility = View.VISIBLE
            } else {
                updatedAtView.visibility = View.GONE
            }
        }
    }
}
