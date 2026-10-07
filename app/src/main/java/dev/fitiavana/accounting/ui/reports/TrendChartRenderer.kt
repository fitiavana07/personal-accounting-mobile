package dev.fitiavana.accounting.ui.reports

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
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
    private val POSITIVE_COLOR = Color.rgb(46, 125, 50)
    private val NEGATIVE_COLOR = Color.rgb(198, 40, 40)
    private val LINE_COLOR = Color.rgb(25, 118, 210)

    /** A trend needs at least two months to be worth drawing. */
    fun hasEnoughData(points: List<TrendPoint>): Boolean = points.size >= MIN_POINTS

    fun configureLine(chart: LineChart) = configureCommon(chart)

    fun configureBar(chart: BarChart) = configureCommon(chart)

    fun renderNetWorth(chart: LineChart, points: List<TrendPoint>) {
        val entries = points.mapIndexed { index, point -> Entry(index.toFloat(), point.netWorth.toFloat()) }
        val dataSet = LineDataSet(entries, "").apply {
            color = LINE_COLOR
            setCircleColor(LINE_COLOR)
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
        }
        chart.xAxis.valueFormatter = MonthLabelFormatter(points)
        chart.data = LineData(dataSet)
        chart.invalidate()
    }

    fun renderNetIncome(chart: BarChart, points: List<TrendPoint>) {
        val entries = points.mapIndexed { index, point -> BarEntry(index.toFloat(), point.netIncome.toFloat()) }
        val dataSet = BarDataSet(entries, "").apply {
            colors = points.map { if (it.netIncome >= 0) POSITIVE_COLOR else NEGATIVE_COLOR }
            setDrawValues(false)
        }
        chart.xAxis.valueFormatter = MonthLabelFormatter(points)
        chart.data = BarData(dataSet)
        chart.invalidate()
    }

    private fun configureCommon(chart: BarLineChartBase<*>) {
        val textColor = axisTextColor(chart.context)
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(false)
        chart.axisRight.isEnabled = false
        chart.axisLeft.apply {
            this.textColor = textColor
            setDrawGridLines(true)
            valueFormatter = AmountAxisFormatter
        }
        chart.xAxis.apply {
            this.textColor = textColor
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
        }
    }

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
