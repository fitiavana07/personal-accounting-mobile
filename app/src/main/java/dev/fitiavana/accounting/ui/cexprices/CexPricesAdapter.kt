package dev.fitiavana.accounting.ui.cexprices

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import java.util.Locale

class CexPricesAdapter : RecyclerView.Adapter<CexPricesAdapter.ViewHolder>() {

    private var rows: List<CexPriceRow> = emptyList()

    fun submit(rows: List<CexPriceRow>) {
        this.rows = rows
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_cex_price, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(rows[position], position)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val divider = view.findViewById<View>(R.id.divider_cex)
        private val name = view.findViewById<TextView>(R.id.text_cex_name)
        private val price = view.findViewById<TextView>(R.id.text_cex_price)
        private val detail = view.findViewById<TextView>(R.id.text_cex_detail)

        fun bind(row: CexPriceRow, position: Int) {
            val context = itemView.context
            divider.visibility = if (position == 0) View.GONE else View.VISIBLE
            name.text = row.cex.displayName
            price.text = row.price?.let(CexPriceFormatter::format)
                ?: context.getString(R.string.cex_price_unavailable)
            detail.text = detailText(row, context.getString(R.string.cex_tag_lowest), context.getString(R.string.cex_tag_highest))
            detail.setTextColor(
                ContextCompat.getColor(context, if (row.isLowest) R.color.gain else if (row.isHighest) R.color.loss else R.color.metrics_card_label)
            )
        }

        private fun detailText(row: CexPriceRow, lowestTag: String, highestTag: String): String {
            if (row.isLowest) return lowestTag
            val diff = row.diffPercent?.let { String.format(Locale.US, "%+.2f%%", it) } ?: return ""
            return if (row.isHighest) "$diff · $highestTag" else diff
        }
    }
}
