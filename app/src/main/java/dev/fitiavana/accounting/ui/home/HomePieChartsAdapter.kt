package dev.fitiavana.accounting.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import dev.fitiavana.accounting.R

/**
 * Single-item header showing the "By Liquidity" and "Assets" pie charts side by side in a
 * horizontally scrolling row, hidden entirely when both are empty.
 */
class HomePieChartsAdapter : RecyclerView.Adapter<HomePieChartsAdapter.ViewHolder>() {

    private var liquiditySlices: List<AssetSlice> = emptyList()
    private var assetSlices: List<AssetSlice> = emptyList()

    fun submitLiquiditySlices(list: List<AssetSlice>) {
        liquiditySlices = list
        onDataChanged()
    }

    fun submitAssetSlices(list: List<AssetSlice>) {
        assetSlices = list
        onDataChanged()
    }

    private fun onDataChanged() = notifyDataSetChanged()

    override fun getItemCount() = if (liquiditySlices.isEmpty() && assetSlices.isEmpty()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_pie_charts_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(liquiditySlices, assetSlices)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val liquidityChart: PieChart = view.findViewById(R.id.chart_liquidity_pie)
        private val assetsChart: PieChart = view.findViewById(R.id.chart_assets_pie)

        init {
            PieChartRenderer.configure(liquidityChart)
            PieChartRenderer.configure(assetsChart)
        }

        fun bind(liquiditySlices: List<AssetSlice>, assetSlices: List<AssetSlice>) {
            val liquidityTitle =
                itemView.context.getString(R.string.home_liquidity_pie_chart_center)
            PieChartRenderer.render(liquidityChart, liquiditySlices, liquidityTitle)
            PieChartRenderer.render(assetsChart, assetSlices, "Assets")
        }
    }
}
