package dev.fitiavana.accounting.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

/**
 * The gain/loss block: a portfolio summary header on top, then one row per
 * account. The header is the first position, so row `n` is `items[n - 1]`.
 */
class HomeAdapter(
    private val onItemClick: (HomeItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<HomeItem> = emptyList()
    private var summary: HomeGainLossSummary? = null

    fun submitList(list: List<HomeItem>) {
        items = list
        summary = HomeGainLossSummary.of(list)
        notifyDataSetChanged()
    }

    override fun getItemCount() = if (items.isEmpty()) 0 else items.size + 1

    override fun getItemViewType(position: Int) = if (position == 0) TYPE_SUMMARY else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SUMMARY) {
            SummaryViewHolder(inflater.inflate(R.layout.item_home_summary, parent, false))
        } else {
            RowViewHolder(inflater.inflate(R.layout.item_home, parent, false), onItemClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SummaryViewHolder -> holder.bind(summary)
            is RowViewHolder -> holder.bind(items[position - 1])
        }
    }

    class SummaryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val gainLossArView: TextView = view.findViewById(R.id.text_home_summary_gain_loss_ar)
        private val detailView: View = view.findViewById(R.id.container_home_summary_detail)
        private val percentView: TextView = view.findViewById(R.id.text_home_summary_percent)
        private val investedView: TextView = view.findViewById(R.id.text_home_summary_invested)

        fun bind(summary: HomeGainLossSummary?) {
            val context = itemView.context
            if (summary == null) {
                gainLossArView.visibility = View.GONE
                detailView.visibility = View.GONE
                return
            }
            gainLossArView.visibility = View.VISIBLE
            detailView.visibility = View.VISIBLE
            bindSigned(
                context, gainLossArView, summary.gainLossAr,
                GainLossFormatter.formatSignedAmountAr(summary.gainLossAr), pill = false
            )
            bindSigned(
                context, percentView, summary.gainLossPercent,
                summary.gainLossPercent?.let { GainLossFormatter.formatSignedPercent(it) }, pill = true
            )
            investedView.text = context.getString(
                R.string.home_gain_loss_invested,
                UiUtils.formatAmountAr(context, summary.bookValueAr)
            )
        }
    }

    class RowViewHolder(view: View, private val onItemClick: (HomeItem) -> Unit) : RecyclerView.ViewHolder(view) {
        private val nameView: TextView = view.findViewById(R.id.text_home_account_name)
        private val balanceView: TextView = view.findViewById(R.id.text_home_account_balance)
        private val gainLossAmountView: TextView = view.findViewById(R.id.text_home_gain_loss_amount)
        private val gainLossPercentView: TextView = view.findViewById(R.id.text_home_gain_loss_percent)
        private val gainLossArView: TextView = view.findViewById(R.id.text_home_gain_loss_ar)

        fun bind(item: HomeItem) {
            val context = itemView.context
            nameView.text = item.accountName
            balanceView.text = item.instrumentBalanceFormatted

            itemView.setOnClickListener { onItemClick(item) }

            bindSigned(
                context, gainLossArView, item.gainLossAr,
                item.gainLossAr?.let { GainLossFormatter.formatSignedAmountAr(it) }, pill = false
            )
            bindSigned(
                context, gainLossPercentView, item.gainLossPercent,
                item.gainLossPercent?.let { GainLossFormatter.formatSignedPercent(it) }, pill = true
            )
            gainLossAmountView.text = item.gainLoss?.let {
                GainLossFormatter.formatSignedAmount(it, item.intermediaryInstrument)
            }
            gainLossAmountView.visibility = if (item.gainLoss != null) View.VISIBLE else View.GONE
        }
    }

    private companion object {
        const val TYPE_SUMMARY = 0
        const val TYPE_ROW = 1

        /** Colors [view] green/red by the sign of [value], optionally as a tinted pill; hides it when [text] is null. */
        fun bindSigned(context: Context, view: TextView, value: Double?, text: String?, pill: Boolean) {
            if (value == null || text == null) {
                view.visibility = View.GONE
                return
            }
            val gain = value >= 0
            view.text = text
            view.setTextColor(ContextCompat.getColor(context, if (gain) R.color.gain else R.color.loss))
            if (pill) view.setBackgroundResource(if (gain) R.drawable.bg_pill_gain else R.drawable.bg_pill_loss)
            view.visibility = View.VISIBLE
        }
    }
}
