package dev.fitiavana.accounting.ui.earn

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.balances.YieldAmounts
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import dev.fitiavana.accounting.ui.common.UiUtils

/**
 * A totals card (monthly base-currency interest of every account) followed by one card per Earn account.
 * Tapping an account with an instrument reveals its interest in the instrument's own units.
 */
class EarnAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var totals: EarnTotals? = null
    private var items: List<EarnItem> = emptyList()
    /** Accounts whose native-instrument interest is open; kept here so it survives rebinding and data refreshes. */
    private val expanded = mutableSetOf<String>()

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
            ItemViewHolder(inflater.inflate(R.layout.item_earn, parent, false), expanded)
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
            itemView.findViewById<TextView>(R.id.text_earn_totals_monthly).text =
                UiUtils.formatAmountAr(context, totals.monthly)
            itemView.findViewById<TextView>(R.id.text_earn_totals_secondary).text = listOf(
                context.getString(R.string.earn_per_day, UiUtils.formatAmountAr(context, totals.daily)),
                context.getString(R.string.earn_per_year, UiUtils.formatAmountAr(context, totals.yearly))
            ).joinToString(SEPARATOR)
        }
    }

    class ItemViewHolder(view: View, private val expanded: MutableSet<String>) : RecyclerView.ViewHolder(view) {
        private val nativeView = itemView.findViewById<TextView>(R.id.text_earn_native)

        fun bind(item: EarnItem) {
            val context = itemView.context
            itemView.findViewById<TextView>(R.id.text_earn_name).text = item.account.name
            itemView.findViewById<TextView>(R.id.text_earn_apr).text =
                context.getString(R.string.account_apr, TransactionDisplay.formatApr(item.aprPercent))
            val balanceParts = listOf(UiUtils.formatAmountAr(context, item.balance)) + nativeParts(
                item, item.instrumentBalance, item.intermediaryBalance
            )
            itemView.findViewById<TextView>(R.id.text_earn_balance).text =
                context.getString(R.string.earn_balance, balanceParts.joinToString(SEPARATOR))
            itemView.findViewById<TextView>(R.id.text_earn_daily).text = UiUtils.formatAmountAr(context, item.daily.base)
            itemView.findViewById<TextView>(R.id.text_earn_monthly).text = UiUtils.formatAmountAr(context, item.monthly.base)
            itemView.findViewById<TextView>(R.id.text_earn_yearly).text = UiUtils.formatAmountAr(context, item.yearly.base)
            itemView.findViewById<ProgressBar>(R.id.progress_earn_share).progress = item.yearlySharePercent

            nativeView.text = listOf(
                context.getString(R.string.earn_native_day, nativeParts(item, item.daily).joinToString(SEPARATOR)),
                context.getString(R.string.earn_native_month, nativeParts(item, item.monthly).joinToString(SEPARATOR)),
                context.getString(R.string.earn_native_year, nativeParts(item, item.yearly).joinToString(SEPARATOR))
            ).joinToString("\n")
            val expandable = item.instrument != null
            nativeView.visibility = if (expandable && item.account.id in expanded) View.VISIBLE else View.GONE
            itemView.setOnClickListener(if (expandable) View.OnClickListener { toggle(item.account.id) } else null)
            // setOnClickListener always makes a view clickable, so undo that for rows with nothing to expand.
            itemView.isClickable = expandable
        }

        private fun toggle(accountId: String) {
            if (!expanded.remove(accountId)) expanded.add(accountId)
            nativeView.visibility = if (accountId in expanded) View.VISIBLE else View.GONE
        }

        private fun nativeParts(item: EarnItem, amounts: YieldAmounts): List<String> =
            nativeParts(item, amounts.instrument, amounts.intermediary)

        /** The instrument then the intermediary amount, each only when the account has that instrument. */
        private fun nativeParts(item: EarnItem, instrument: Long?, intermediary: Long?): List<String> =
            listOfNotNull(formatNative(instrument, item.instrument), formatNative(intermediary, item.intermediaryInstrument))

        private fun formatNative(amount: Long?, instrument: Instrument?): String? =
            if (amount != null && instrument != null) TransactionDisplay.formatInstrumentAmount(amount, instrument) else null
    }

    companion object {
        const val VIEW_TYPE_TOTALS = 0
        const val VIEW_TYPE_ITEM = 1
        private const val SEPARATOR = " · "
    }
}
