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
}
