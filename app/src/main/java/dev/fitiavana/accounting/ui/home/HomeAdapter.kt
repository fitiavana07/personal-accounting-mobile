package dev.fitiavana.accounting.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R

class HomeAdapter(
    private val onItemClick: (HomeItem) -> Unit
) : RecyclerView.Adapter<HomeAdapter.ViewHolder>() {

    private var items: List<HomeItem> = emptyList()

    fun submitList(list: List<HomeItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View, private val onItemClick: (HomeItem) -> Unit) : RecyclerView.ViewHolder(view) {
        private val nameView: TextView = view.findViewById(R.id.text_home_account_name)
        private val gainLossAmountView: TextView = view.findViewById(R.id.text_home_gain_loss_amount)
        private val gainLossPercentView: TextView = view.findViewById(R.id.text_home_gain_loss_percent)
        private val gainLossArView: TextView = view.findViewById(R.id.text_home_gain_loss_ar)

        fun bind(item: HomeItem) {
            val context = itemView.context
            nameView.text = item.accountName

            itemView.setOnClickListener { onItemClick(item) }

            if (item.gainLoss != null) {
                val color = if (item.gainLoss >= 0) R.color.gain else R.color.loss
                gainLossAmountView.text = GainLossFormatter.formatSignedAmount(item.gainLoss, item.intermediaryInstrument)
                gainLossAmountView.setTextColor(ContextCompat.getColor(context, color))
                gainLossAmountView.visibility = View.VISIBLE
            } else {
                gainLossAmountView.visibility = View.GONE
            }

            if (item.gainLossAr != null) {
                val color = if (item.gainLossAr >= 0) R.color.gain else R.color.loss
                gainLossArView.text = GainLossFormatter.formatSignedAmountAr(item.gainLossAr)
                gainLossArView.setTextColor(ContextCompat.getColor(context, color))
                gainLossArView.visibility = View.VISIBLE
            } else {
                gainLossArView.visibility = View.GONE
            }

            if (item.gainLossPercent != null) {
                val color = if (item.gainLossPercent >= 0) R.color.gain else R.color.loss
                gainLossPercentView.text = GainLossFormatter.formatSignedPercent(item.gainLossPercent)
                gainLossPercentView.setTextColor(ContextCompat.getColor(context, color))
                gainLossPercentView.visibility = View.VISIBLE
            } else {
                gainLossPercentView.visibility = View.GONE
            }
        }
    }
}
