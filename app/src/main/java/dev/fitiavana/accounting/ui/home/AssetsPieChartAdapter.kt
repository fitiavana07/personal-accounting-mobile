package dev.fitiavana.accounting.ui.home

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import dev.fitiavana.accounting.R
import java.text.DecimalFormat

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
            chart.setCenterTextSize(16f)
            chart.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (chart.width > 0 && chart.height != chart.width) {
                        chart.layoutParams = chart.layoutParams.apply { height = chart.width }
                    }
                }
            })
        }

        fun bind(slices: List<AssetSlice>) {
            val entries = slices.map { PieEntry(Math.abs(it.amount).toFloat(), it.name) }
            val sliceColors = slices.indices.map { AssetPalette.colorFor(it) }
            val dataSet = PieDataSet(entries, "").apply {
                colors = sliceColors
                setValueTextColors(sliceColors.map { readableLabelColorFor(it) })
                valueTextSize = 16f
                valueFormatter = MinPercentFormatter(MIN_VISIBLE_PERCENT)
            }
            chart.data = PieData(dataSet)
            chart.centerText = buildCenterText(slices)
            chart.invalidate()
        }

        private fun buildCenterText(slices: List<AssetSlice>): CharSequence {
            val totalAssets = slices.sumOf { it.amount }
            val subtitle = CompactNumberFormatter.format(totalAssets)
            val text = "Assets\n$subtitle"
            return SpannableStringBuilder(text).apply {
                val subtitleStart = text.indexOf('\n') + 1
                setSpan(
                    ForegroundColorSpan(resolveTextColorPrimary(chart.context)),
                    0,
                    subtitleStart - 1,
                    0
                )
                setSpan(
                    ForegroundColorSpan(resolveTextColorSecondary(chart.context)),
                    subtitleStart,
                    text.length,
                    0
                )
                setSpan(RelativeSizeSpan(2.5f), subtitleStart, text.length, 0)
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    subtitleStart,
                    text.length,
                    0
                )
            }
        }
    }

    /** Percent value label formatter that hides labels for slices smaller than [minPercent]. */
    private class MinPercentFormatter(private val minPercent: Float) :
        ValueFormatter() {
        private val decimalFormat = DecimalFormat("###,##0.0")

        override fun getPieLabel(value: Float, pieEntry: PieEntry?): String =
            if (value < minPercent) "" else "${decimalFormat.format(value)}%"
    }

    companion object {
        private const val MIN_VISIBLE_PERCENT = 5f

        /** Black or white, whichever is more readable against [backgroundColor]. */
        private fun readableLabelColorFor(backgroundColor: Int): Int {
            val luminance = (0.299 * Color.red(backgroundColor) +
                0.587 * Color.green(backgroundColor) +
                0.114 * Color.blue(backgroundColor)) / 255
            return if (luminance > 0.5) Color.BLACK else Color.WHITE
        }

        private fun resolveTextColorPrimary(context: android.content.Context): Int =
            resolveThemeColor(context, android.R.attr.textColorPrimary)

        private fun resolveTextColorSecondary(context: android.content.Context): Int =
            resolveThemeColor(context, android.R.attr.textColorSecondary)

        private fun resolveThemeColor(
            context: android.content.Context,
            attr: Int
        ): Int {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(attr, typedValue, true)
            return if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }
    }
}
