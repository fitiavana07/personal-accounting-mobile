package dev.fitiavana.accounting.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.transactions.TransactionDisplay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeAdapter : RecyclerView.Adapter<HomeAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private var items: List<HomeItem> = emptyList()

    fun submitList(list: List<HomeItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], dateFormat)
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameView: TextView = view.findViewById(R.id.text_home_account_name)
        private val currentValueView: TextView = view.findViewById(R.id.text_home_current_value)
        private val currentRateView: TextView = view.findViewById(R.id.text_home_current_rate)
        private val bookValueView: TextView = view.findViewById(R.id.text_home_book_value)
        private val bookRateView: TextView = view.findViewById(R.id.text_home_book_rate)
        private val rateUpdatedAtView: TextView = view.findViewById(R.id.text_home_rate_updated_at)
        private val gainLossAmountView: TextView = view.findViewById(R.id.text_home_gain_loss_amount)
        private val gainLossPercentView: TextView = view.findViewById(R.id.text_home_gain_loss_percent)

        fun bind(item: HomeItem, dateFormat: SimpleDateFormat) {
            val context = itemView.context
            nameView.text = item.accountName
            val bookValueText = TransactionDisplay.formatInstrumentAmount(
                Math.round(item.bookValue * Math.pow(10.0, item.intermediaryInstrument.decimalPlaces.toDouble())),
                item.intermediaryInstrument
            )
            bookValueView.text = context.getString(R.string.home_label_book_value, bookValueText)

            if (item.bookRate != null) {
                bookRateView.text = context.getString(R.string.home_label_book_price, item.bookRate)
                bookRateView.visibility = View.VISIBLE
            } else {
                bookRateView.visibility = View.GONE
            }

            if (item.currentValue != null) {
                val marketValueText = TransactionDisplay.formatInstrumentAmount(
                    Math.round(item.currentValue * Math.pow(10.0, item.intermediaryInstrument.decimalPlaces.toDouble())),
                    item.intermediaryInstrument
                )
                currentValueView.text = context.getString(R.string.home_label_market_value, marketValueText)
                currentValueView.visibility = View.VISIBLE
            } else {
                currentValueView.visibility = View.GONE
            }

            if (item.currentRate != null) {
                currentRateView.text = context.getString(R.string.home_label_market_price, item.currentRate)
                currentRateView.visibility = View.VISIBLE
            } else {
                currentRateView.visibility = View.GONE
            }

            rateUpdatedAtView.text = if (item.rateFetchedAt != null) {
                context.getString(R.string.home_rate_updated_at, dateFormat.format(Date(item.rateFetchedAt)))
            } else {
                context.getString(R.string.home_rate_never_updated)
            }

            if (item.gainLoss != null) {
                val color = if (item.gainLoss >= 0) R.color.gain else R.color.loss
                gainLossAmountView.text = GainLossCalculator.formatSignedAmount(item.gainLoss, item.intermediaryInstrument)
                gainLossAmountView.setTextColor(ContextCompat.getColor(context, color))
                gainLossAmountView.visibility = View.VISIBLE
            } else {
                gainLossAmountView.visibility = View.GONE
            }

            if (item.gainLossPercent != null) {
                val color = if (item.gainLossPercent >= 0) R.color.gain else R.color.loss
                gainLossPercentView.text = GainLossCalculator.formatSignedPercent(item.gainLossPercent)
                gainLossPercentView.setTextColor(ContextCompat.getColor(context, color))
                gainLossPercentView.visibility = View.VISIBLE
            } else {
                gainLossPercentView.visibility = View.GONE
            }
        }
    }
}
