package dev.fitiavana.accounting.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.DecimalFormat

/** Shared setup/rendering for the Home tab's asset-allocation pie charts (by account, by liquidity level). */
object PieChartRenderer {

    private const val MIN_VISIBLE_PERCENT = 5f

    /** One-time chart setup, including forcing a square aspect ratio to match its measured width. */
    fun configure(chart: PieChart) {
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

    fun render(chart: PieChart, slices: List<AssetSlice>, centerTitle: String) {
        val entries = slices.map { PieEntry(Math.abs(it.amount).toFloat(), it.name) }
        val sliceColors = slices.indices.map { AssetPalette.colorFor(it) }
        val dataSet = PieDataSet(entries, "").apply {
            colors = sliceColors
            setValueTextColors(sliceColors.map { readableLabelColorFor(it) })
            valueTextSize = 16f
            valueFormatter = MinPercentFormatter(MIN_VISIBLE_PERCENT)
        }
        chart.data = PieData(dataSet)
        chart.centerText = buildCenterText(chart.context, slices, centerTitle)
        chart.invalidate()
    }

    private fun buildCenterText(
        context: Context,
        slices: List<AssetSlice>,
        centerTitle: String
    ): CharSequence {
        val total = slices.sumOf { it.amount }
        val subtitle = CompactNumberFormatter.format(total)
        val text = "$centerTitle\n$subtitle"
        return SpannableStringBuilder(text).apply {
            val subtitleStart = text.indexOf('\n') + 1
            setSpan(ForegroundColorSpan(resolveTextColorPrimary(context)), 0, subtitleStart - 1, 0)
            setSpan(ForegroundColorSpan(resolveTextColorSecondary(context)), subtitleStart, text.length, 0)
            setSpan(RelativeSizeSpan(2.5f), subtitleStart, text.length, 0)
            setSpan(StyleSpan(Typeface.BOLD), subtitleStart, text.length, 0)
        }
    }

    /** Percent value label formatter that hides labels for slices smaller than [minPercent]. */
    private class MinPercentFormatter(private val minPercent: Float) : ValueFormatter() {
        private val decimalFormat = DecimalFormat("###,##0.0")

        override fun getPieLabel(value: Float, pieEntry: PieEntry?): String =
            if (value < minPercent) "" else "${decimalFormat.format(value)}%"
    }

    /** Black or white, whichever is more readable against [backgroundColor]. */
    private fun readableLabelColorFor(backgroundColor: Int): Int {
        val luminance = (0.299 * Color.red(backgroundColor) +
            0.587 * Color.green(backgroundColor) +
            0.114 * Color.blue(backgroundColor)) / 255
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    private fun resolveTextColorPrimary(context: Context): Int =
        resolveThemeColor(context, android.R.attr.textColorPrimary)

    /** Pure white in dark mode / pure black in light mode, for stronger contrast than the themed secondary text color. */
    private fun resolveTextColorSecondary(context: Context): Int {
        val isNightMode = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        return if (isNightMode) Color.WHITE else Color.BLACK
    }

    private fun resolveThemeColor(context: Context, attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }
}
