package dev.fitiavana.accounting.ui.reports

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.formatter.ValueFormatter
import dev.fitiavana.accounting.ui.home.CompactNumberFormatter

/** Shared setup/rendering for the Reports tab's net worth (line) and net income (bar) trend charts. */
object TrendChartRenderer {

    private const val MIN_POINTS = 2
    private const val BAR_WIDTH = 0.6f
    /** Low tension keeps the curve from overshooting between points, which would misstate the data. */
    private const val CURVE_INTENSITY = 0.1f
    private const val ANIMATION_MS = 600
    private const val TEXT_SIZE_SP = 10f
    private val POSITIVE_COLOR = Color.rgb(46, 125, 50)
    private val NEGATIVE_COLOR = Color.rgb(198, 40, 40)
    private val LINE_COLOR = Color.rgb(25, 118, 210)

    /** A trend needs at least two months to be worth drawing. */
    fun hasEnoughData(points: List<TrendPoint>): Boolean = points.size >= MIN_POINTS

    /**
     * Visibility of the charts block. While the trend is still loading ([points] null) the block is
     * INVISIBLE, not GONE, so it keeps its height and the report below does not jump when it arrives.
     */
    fun blockVisibility(points: List<TrendPoint>?): Int = when {
        points == null -> View.INVISIBLE
        hasEnoughData(points) -> View.VISIBLE
        else -> View.GONE
    }

    /** The spinner centered over the reserved chart space: shown only until the trend has loaded. */
    fun loadingVisibility(points: List<TrendPoint>?): Int =
        if (points == null) View.VISIBLE else View.GONE

    fun configureLine(chart: LineChart) = configureCommon(chart)

    fun configureBar(chart: BarChart) = configureCommon(chart)

    fun renderNetWorth(chart: LineChart, points: List<TrendPoint>) {
        val entries = points.mapIndexed { index, point -> Entry(index.toFloat(), point.netWorth.toFloat()) }
        val dataSet = LineDataSet(entries, "").apply {
            color = LINE_COLOR
            lineWidth = 2.5f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = CURVE_INTENSITY
            setDrawCircles(false)
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = LINE_COLOR
            fillAlpha = 40
        }
        chart.xAxis.valueFormatter = MonthLabelFormatter(points)
        chart.data = LineData(dataSet)
        chart.animateX(ANIMATION_MS)
    }

    fun renderNetIncome(chart: BarChart, points: List<TrendPoint>) {
        val entries = points.mapIndexed { index, point -> BarEntry(index.toFloat(), point.netIncome.toFloat()) }
        val dataSet = BarDataSet(entries, "").apply {
            colors = points.map { if (it.netIncome >= 0) POSITIVE_COLOR else NEGATIVE_COLOR }
            setDrawValues(false)
        }
        chart.xAxis.valueFormatter = MonthLabelFormatter(points)
        chart.data = BarData(dataSet).apply { barWidth = BAR_WIDTH }
        chart.animateY(ANIMATION_MS)
    }

    private fun configureCommon(chart: BarLineChartBase<*>) {
        val textColor = axisTextColor(chart.context)
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(false)
        chart.axisRight.isEnabled = false
        chart.setExtraOffsets(0f, 8f, 8f, 8f)
        chart.axisLeft.apply {
            this.textColor = textColor
            textSize = TEXT_SIZE_SP
            setDrawAxisLine(false)
            setDrawGridLines(true)
            gridColor = gridColor(textColor)
            enableGridDashedLine(8f, 8f, 0f)
            setDrawZeroLine(true)
            zeroLineColor = textColor
            zeroLineWidth = 1f
            valueFormatter = AmountAxisFormatter
        }
        chart.xAxis.apply {
            this.textColor = textColor
            textSize = TEXT_SIZE_SP
            setDrawAxisLine(false)
            yOffset = 6f
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
        }
    }

    /** The text colour at low opacity, so the grid stays in the background. */
    private fun gridColor(textColor: Int): Int = Color.argb(40, Color.red(textColor), Color.green(textColor), Color.blue(textColor))

    /** White on dark theme, black on light theme, matching the Home pie charts' contrast approach. */
    private fun axisTextColor(context: Context): Int {
        val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (isNightMode) Color.WHITE else Color.BLACK
    }

    /** Three-letter month name for each point's x index. */
    private class MonthLabelFormatter(private val points: List<TrendPoint>) : ValueFormatter() {
        override fun getFormattedValue(value: Float): String =
            points.getOrNull(value.toInt())?.let { ReportPeriodSelector.monthName(it.yearMonth.month).take(3) }
                ?: ""
    }

    private object AmountAxisFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String = CompactNumberFormatter.format(value.toLong())
    }
}
