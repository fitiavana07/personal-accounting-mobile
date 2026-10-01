package dev.fitiavana.accounting.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.p2pprices.P2pPrices
import dev.fitiavana.accounting.network.p2p.P2pAd
import dev.fitiavana.accounting.ui.common.TransactionDisplay

/**
 * Single-row block under the shortcuts: top 3 Binance P2P Buy ads on the left, top 3 Sell ads on the right,
 * with a payment method filter label ([onFilterClick]) in the header.
 */
class HomeP2pPricesAdapter(
    private val onFilterClick: () -> Unit = {}
) : RecyclerView.Adapter<HomeP2pPricesAdapter.ViewHolder>() {

    private var prices: P2pPrices? = null

    fun submit(prices: P2pPrices?) {
        this.prices = prices
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_p2p_prices, parent, false)
        view.findViewById<View>(R.id.text_p2p_filter).setOnClickListener { onFilterClick() }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val context = holder.itemView.context
        holder.filter.text = context.getString(
            R.string.home_p2p_filter_format,
            prices?.filter?.name ?: context.getString(R.string.home_p2p_filter_all)
        )
        holder.bind(holder.buy, prices?.buy)
        holder.bind(holder.sell, prices?.sell)
    }

    /** One ad line: the price, then the advertiser name and the order limits. */
    class Line(val price: TextView, val name: TextView, val limits: TextView)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val filter: TextView = view.findViewById(R.id.text_p2p_filter)
        val buy = listOf(
            line(view, R.id.text_p2p_buy_1, R.id.text_p2p_buy_1_name, R.id.text_p2p_buy_1_limits),
            line(view, R.id.text_p2p_buy_2, R.id.text_p2p_buy_2_name, R.id.text_p2p_buy_2_limits),
            line(view, R.id.text_p2p_buy_3, R.id.text_p2p_buy_3_name, R.id.text_p2p_buy_3_limits)
        )
        val sell = listOf(
            line(view, R.id.text_p2p_sell_1, R.id.text_p2p_sell_1_name, R.id.text_p2p_sell_1_limits),
            line(view, R.id.text_p2p_sell_2, R.id.text_p2p_sell_2_name, R.id.text_p2p_sell_2_limits),
            line(view, R.id.text_p2p_sell_3, R.id.text_p2p_sell_3_name, R.id.text_p2p_sell_3_limits)
        )

        private fun line(view: View, price: Int, name: Int, limits: Int) =
            Line(view.findViewById(price), view.findViewById(name), view.findViewById(limits))

        fun bind(lines: List<Line>, ads: List<P2pAd>?) {
            lines.forEachIndexed { i, line ->
                val ad = ads?.getOrNull(i)
                line.price.text = ad?.let { TransactionDisplay.formatAmount(Math.round(it.price)) } ?: PLACEHOLDER
                line.name.text = ad?.let { P2pAdFormatter.advertiser(it) } ?: ""
                line.limits.text = ad?.let { P2pAdFormatter.limits(it) } ?: ""
            }
        }
    }

    private companion object {
        const val PLACEHOLDER = "–"
    }
}
