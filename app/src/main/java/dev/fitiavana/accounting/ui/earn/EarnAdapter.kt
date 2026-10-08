package dev.fitiavana.accounting.ui.earn

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.balances.YieldAmounts
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import dev.fitiavana.accounting.ui.common.UiUtils

/** A totals card (base-currency interest of every account) followed by one card per Earn account. */
class EarnAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var totals: EarnTotals? = null
    private var items: List<EarnItem> = emptyList()

    fun submit(state: EarnState) {
        items = state.items
        totals = state.totals.takeIf { state.items.isNotEmpty() }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = if (totals == null) 0 else items.size + 1

    override fun getItemViewType(position: Int): Int = if (position == 0) VIEW_TYPE_TOTALS else VIEW_TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_TOTALS) {
            TotalsViewHolder(inflater.inflate(R.layout.item_earn_totals, parent, false))
        } else {
            ItemViewHolder(inflater.inflate(R.layout.item_earn, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TotalsViewHolder -> totals?.let { holder.bind(it) }
            is ItemViewHolder -> holder.bind(items[position - 1])
        }
    }

    class TotalsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(totals: EarnTotals) {
            val context = itemView.context
            itemView.findViewById<TextView>(R.id.text_earn_totals_daily).text =
                context.getString(R.string.earn_daily, UiUtils.formatAmountAr(context, totals.daily))
            itemView.findViewById<TextView>(R.id.text_earn_totals_monthly).text =
                context.getString(R.string.earn_monthly, UiUtils.formatAmountAr(context, totals.monthly))
            itemView.findViewById<TextView>(R.id.text_earn_totals_yearly).text =
                context.getString(R.string.earn_yearly, UiUtils.formatAmountAr(context, totals.yearly))
        }
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: EarnItem) {
            val context = itemView.context
            itemView.findViewById<TextView>(R.id.text_earn_name).text = item.account.name
            itemView.findViewById<TextView>(R.id.text_earn_apr).text =
                context.getString(R.string.account_apr, TransactionDisplay.formatApr(item.aprPercent))
            itemView.findViewById<TextView>(R.id.text_earn_balance).text = context.getString(
                R.string.earn_balance,
                joinAmounts(
                    context, item,
                    base = item.balance,
                    instrument = item.instrumentBalance,
                    intermediary = item.intermediaryBalance
                )
            )
            itemView.findViewById<TextView>(R.id.text_earn_daily).text =
                context.getString(R.string.earn_daily, joinAmounts(context, item, item.daily))
            itemView.findViewById<TextView>(R.id.text_earn_monthly).text =
                context.getString(R.string.earn_monthly, joinAmounts(context, item, item.monthly))
            itemView.findViewById<TextView>(R.id.text_earn_yearly).text =
                context.getString(R.string.earn_yearly, joinAmounts(context, item, item.yearly))
        }

        private fun joinAmounts(context: Context, item: EarnItem, amounts: YieldAmounts): String =
            joinAmounts(context, item, amounts.base, amounts.instrument, amounts.intermediary)

        /** "Ar 1,200 · 0.5 BTC · 100 USD": base first, then the instrument and intermediary amounts when the account has them. */
        private fun joinAmounts(
            context: Context,
            item: EarnItem,
            base: Long,
            instrument: Long?,
            intermediary: Long?
        ): String {
            val parts = mutableListOf(UiUtils.formatAmountAr(context, base))
            formatNative(instrument, item.instrument)?.let { parts += it }
            formatNative(intermediary, item.intermediaryInstrument)?.let { parts += it }
            return parts.joinToString(SEPARATOR)
        }

        private fun formatNative(amount: Long?, instrument: Instrument?): String? =
            if (amount != null && instrument != null) TransactionDisplay.formatInstrumentAmount(amount, instrument) else null
    }

    companion object {
        const val VIEW_TYPE_TOTALS = 0
        const val VIEW_TYPE_ITEM = 1
        private const val SEPARATOR = " · "
    }
}
