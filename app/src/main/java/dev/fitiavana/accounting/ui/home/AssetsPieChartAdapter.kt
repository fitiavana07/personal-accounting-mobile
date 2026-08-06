package dev.fitiavana.accounting.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import dev.fitiavana.accounting.R

/** Single-item header showing an asset allocation pie chart, hidden entirely when there are no asset slices. */
class AssetsPieChartAdapter : RecyclerView.Adapter<AssetsPieChartAdapter.ViewHolder>() {

    private var slices: List<AssetSlice> = emptyList()

    fun submitList(list: List<AssetSlice>) {
        slices = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = if (slices.isEmpty()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_assets_pie_chart, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(slices)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val chart: PieChart = view as PieChart

        init {
            chart.description.isEnabled = false
            chart.setUsePercentValues(true)
            chart.setDrawEntryLabels(false)
            chart.legend.isEnabled = false
            chart.setHoleColor(Color.TRANSPARENT)
            chart.setEntryLabelColor(Color.WHITE)
        }

        fun bind(slices: List<AssetSlice>) {
            val entries = slices.map { PieEntry(Math.abs(it.amount).toFloat(), it.name) }
            val dataSet = PieDataSet(entries, "").apply {
                colors = slices.indices.map { AssetPalette.colorFor(it) }
                valueTextColor = Color.WHITE
                valueFormatter = PercentFormatter(chart)
            }
            chart.data = PieData(dataSet)
            chart.invalidate()
        }
    }
}
