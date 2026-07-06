package dev.fitiavana.accounting.ui.balances

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.ui.transactions.TransactionDisplay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BalanceItem(
    val accountId: String,
    val accountName: String,
    val balance: Long,
    val instrumentBalance: Long,
    val instrument: Instrument?,
    val intermediaryBalance: Long = 0,
    val intermediaryInstrument: Instrument? = null,
    val updatedAt: Long
)

class BalancesAdapter : RecyclerView.Adapter<BalancesAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private var items: List<BalanceItem> = emptyList()

    fun submitList(list: List<BalanceItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_balance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], dateFormat)
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameView: TextView = view.findViewById(R.id.text_balance_account_name)
        private val updatedAtView: TextView = view.findViewById(R.id.text_balance_updated_at)
        private val amountView: TextView = view.findViewById(R.id.text_balance_amount)
        private val instrumentAmountView: TextView = view.findViewById(R.id.text_balance_instrument_amount)
        private val exchangeRateView: TextView = view.findViewById(R.id.text_balance_exchange_rate)

        fun bind(item: BalanceItem, dateFormat: SimpleDateFormat) {
            nameView.text = item.accountName
            updatedAtView.text = dateFormat.format(Date(item.updatedAt))
            amountView.text = "Ar ${TransactionDisplay.formatAmount(item.balance)}"
            if (item.instrument != null) {
                val instrumentText = TransactionDisplay.formatInstrumentAmount(item.instrumentBalance, item.instrument)
                instrumentAmountView.text = if (item.intermediaryInstrument != null) {
                    "$instrumentText · ${TransactionDisplay.formatInstrumentAmount(item.intermediaryBalance, item.intermediaryInstrument)}"
                } else {
                    instrumentText
                }
                instrumentAmountView.visibility = View.VISIBLE
            } else {
                instrumentAmountView.visibility = View.GONE
            }

            val exchangeRate = if (item.instrument != null && item.intermediaryInstrument == null) {
                TransactionDisplay.formatExchangeRate(item.balance, item.instrumentBalance, item.instrument)
            } else {
                null
            }
            if (exchangeRate != null) {
                exchangeRateView.text = exchangeRate
                exchangeRateView.visibility = View.VISIBLE
            } else {
                exchangeRateView.visibility = View.GONE
            }
        }
    }
}
