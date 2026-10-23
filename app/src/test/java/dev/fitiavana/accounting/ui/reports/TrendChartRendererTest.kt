package dev.fitiavana.accounting.ui.reports

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class TrendChartRendererTest {

    private lateinit var context: Context

    private val points = listOf(
        TrendPoint(YearMonth(2025, 0), netWorth = 1_000L, netIncome = 100L),
        TrendPoint(YearMonth(2025, 1), netWorth = 1_200L, netIncome = -50L),
        TrendPoint(YearMonth(2025, 2), netWorth = 1_500L, netIncome = 300L)
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `the block is shown only with at least two points`() {
        assertFalse(TrendChartRenderer.hasEnoughData(emptyList()))
        assertFalse(TrendChartRenderer.hasEnoughData(points.take(1)))
        assertTrue(TrendChartRenderer.hasEnoughData(points.take(2)))
    }

    @Test
    fun `the block keeps its space while the trend is still loading`() {
        assertEquals(View.INVISIBLE, TrendChartRenderer.blockVisibility(null))
    }

    @Test
    fun `the block collapses once loaded with too few points and shows with enough`() {
        assertEquals(View.GONE, TrendChartRenderer.blockVisibility(points.take(1)))
        assertEquals(View.VISIBLE, TrendChartRenderer.blockVisibility(points))
    }

    @Test
    fun `the loading indicator is shown only while the trend is loading`() {
        assertEquals(View.VISIBLE, TrendChartRenderer.loadingVisibility(null))
        assertEquals(View.GONE, TrendChartRenderer.loadingVisibility(emptyList()))
        assertEquals(View.GONE, TrendChartRenderer.loadingVisibility(points))
    }

    @Test
    fun `net worth line has one entry per month with the net worth as value`() {
        val chart = LineChart(context)
        TrendChartRenderer.configureLine(chart)

        TrendChartRenderer.renderNetWorth(chart, points)

        val entries = chart.data.getDataSetByIndex(0).let { set ->
            (0 until set.entryCount).map { set.getEntryForIndex(it) }
        }
        assertEquals(listOf(0f, 1f, 2f), entries.map { it.x })
        assertEquals(listOf(1_000f, 1_200f, 1_500f), entries.map { it.y })
    }

    @Test
    fun `net income bars carry the net income and are coloured by sign`() {
        val chart = BarChart(context)
        TrendChartRenderer.configureBar(chart)

        TrendChartRenderer.renderNetIncome(chart, points)

        val set = chart.data.getDataSetByIndex(0)
        assertEquals(
            listOf(100f, -50f, 300f),
            (0 until set.entryCount).map { set.getEntryForIndex(it).y }
        )
        assertEquals(set.getColor(0), set.getColor(2))
        assertNotEquals(set.getColor(0), set.getColor(1))
    }

    @Test
    fun `x axis labels are short month names`() {
        val chart = LineChart(context)
        TrendChartRenderer.configureLine(chart)

        TrendChartRenderer.renderNetWorth(chart, points)

        val formatter = chart.xAxis.valueFormatter
        assertEquals(
            ReportPeriodSelector.monthName(0).take(3),
            formatter.getFormattedValue(0f)
        )
        assertEquals(
            ReportPeriodSelector.monthName(2).take(3),
            formatter.getFormattedValue(2f)
        )
    }

    @Test
    fun `net worth line is smooth, filled and only marks the latest month`() {
        val chart = LineChart(context)
        TrendChartRenderer.configureLine(chart)

        TrendChartRenderer.renderNetWorth(chart, points)

        val set = chart.data.getDataSetByIndex(0) as com.github.mikephil.charting.data.LineDataSet
        assertEquals(com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER, set.mode)
        assertTrue(set.isDrawFilledEnabled)
        assertFalse(set.isDrawCirclesEnabled)
        assertFalse(set.isDrawValuesEnabled)
    }

    @Test
    fun `charts have a quiet look without axis lines and with dashed grid`() {
        val line = LineChart(context)
        val bar = BarChart(context)
        TrendChartRenderer.configureLine(line)
        TrendChartRenderer.configureBar(bar)

        listOf(line, bar).forEach { chart ->
            assertFalse(chart.axisLeft.isDrawAxisLineEnabled)
            assertFalse(chart.xAxis.isDrawAxisLineEnabled)
            assertTrue(chart.axisLeft.isGridDashedLineEnabled)
        }
    }

    @Test
    fun `charts do not drag or zoom`() {
        val chart = BarChart(context)
        TrendChartRenderer.configureBar(chart)

        assertFalse(chart.isDragEnabled)
        assertFalse(chart.isScaleXEnabled)
        assertFalse(chart.isScaleYEnabled)
        assertFalse(chart.isDoubleTapToZoomEnabled)
    }

    @Test
    fun `the selected month is highlighted on the line chart`() {
        val chart = LineChart(context)
        TrendChartRenderer.configureLine(chart)

        TrendChartRenderer.renderNetWorth(chart, points, YearMonth(2025, 1))

        assertEquals(1f, chart.highlighted.single().x, 0f)
    }

    @Test
    fun `no month is highlighted when the selection is not charted`() {
        val chart = LineChart(context)
        TrendChartRenderer.configureLine(chart)

        TrendChartRenderer.renderNetWorth(chart, points, YearMonth(2030, 1))

        assertTrue(chart.highlighted.isNullOrEmpty())
    }

    @Test
    fun `updateSelection fades the bars other than the selected month`() {
        val chart = BarChart(context)
        TrendChartRenderer.configureBar(chart)
        TrendChartRenderer.renderNetIncome(chart, points)

        TrendChartRenderer.updateSelection(chart, points, YearMonth(2025, 2))

        val set = chart.data.getDataSetByIndex(0)
        assertEquals(255, android.graphics.Color.alpha(set.getColor(2)))
        assertTrue(android.graphics.Color.alpha(set.getColor(0)) < 255)
        assertTrue(android.graphics.Color.alpha(set.getColor(1)) < 255)

        TrendChartRenderer.updateSelection(chart, points, null)

        assertEquals(255, android.graphics.Color.alpha(set.getColor(0)))
    }

    @Test
    fun `selecting a value reports the tapped month`() {
        val chart = LineChart(context)
        TrendChartRenderer.configureLine(chart)
        var selected: YearMonth? = null
        TrendChartRenderer.renderNetWorth(chart, points, null) { selected = it }

        // MPAndroidChart exposes no getter for the listener; read the field the chart notifies on a tap.
        val listener = com.github.mikephil.charting.charts.Chart::class.java
            .getDeclaredField("mSelectionListener").apply { isAccessible = true }
            .get(chart) as com.github.mikephil.charting.listener.OnChartValueSelectedListener
        listener.onValueSelected(com.github.mikephil.charting.data.Entry(2f, 1_500f), null)

        assertEquals(YearMonth(2025, 2), selected)
    }

    @Test
    fun `net income bars are slimmer than the slot and the zero line is drawn`() {
        val chart = BarChart(context)
        TrendChartRenderer.configureBar(chart)

        TrendChartRenderer.renderNetIncome(chart, points)

        assertEquals(0.6f, (chart.data as com.github.mikephil.charting.data.BarData).barWidth, 0.001f)
        assertTrue(chart.axisLeft.isDrawZeroLineEnabled)
    }
}
