package dev.fitiavana.accounting.ui.cexprices

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.cexprices.CexPrice

class CexPricesAdapter : RecyclerView.Adapter<CexPricesAdapter.ViewHolder>() {

    private var prices: List<CexPrice> = emptyList()

    fun submit(prices: List<CexPrice>?) {
        this.prices = prices ?: emptyList()
        notifyDataSetChanged()
    }

    override fun getItemCount() = prices.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_cex_price, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(prices[position])

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.text_cex_name)
        private val price = view.findViewById<TextView>(R.id.text_cex_price)

        fun bind(item: CexPrice) {
            name.text = item.cex.displayName
            price.text = item.price?.let(CexPriceFormatter::format)
                ?: itemView.context.getString(R.string.cex_price_unavailable)
        }
    }
}
