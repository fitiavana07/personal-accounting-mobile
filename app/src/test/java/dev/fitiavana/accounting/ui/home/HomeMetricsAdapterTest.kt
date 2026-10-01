package dev.fitiavana.accounting.ui.home

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class HomeMetricsAdapterTest {

    private lateinit var itemView: View

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = HomeMetricsAdapter()
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.submit(
            HomeMetrics(
                totalEquity = 2_000_000,
                cash = 500_000,
                emergencyFundPercent = 40,
                cashToEquityPercent = 25,
                monthlyExpenses = 100_000,
                cashRunwayMonths = 5.0,
                incomeToExpensesPercent = 120,
                averageMonthlyIncome = 150_000
            )
        )
        adapter.onBindViewHolder(holder, 0)
        itemView = holder.itemView
    }

    private fun rowOf(label: String): View {
        val labels = ArrayList<View>()
        itemView.findViewsWithText(labels, label, View.FIND_VIEWS_WITH_TEXT)
        val labelView = labels.single { (it as TextView).text.toString() == label }
        // label -> cell -> row (pair) or label -> row (single)
        var node = labelView.parent as View
        while (node.parent !== itemView.findViewById<View>(R.id.container_metrics_rows)) {
            node = node.parent as View
        }
        return node
    }

    private fun cellIndexOf(label: String): Int {
        val row = rowOf(label) as ViewGroup
        val labelView = ArrayList<View>().also {
            row.findViewsWithText(it, label, View.FIND_VIEWS_WITH_TEXT)
        }.single { (it as TextView).text.toString() == label }
        val left = row.findViewById<View>(R.id.metric_cell_left)
        return if (labelView.parent === left) 0 else 1
    }

    @Test
    fun `equity and cash share one row`() {
        assertSame(rowOf("Equity"), rowOf("Cash"))
    }

    @Test
    fun `cash to equity and emergency fund share one row`() {
        assertSame(rowOf("Cash to Equity"), rowOf("Emergency Fund"))
    }

    @Test
    fun `avg income and monthly expenses share one row`() {
        assertSame(rowOf("Avg. Income"), rowOf("Mo. Spend"))
    }

    @Test
    fun `income to expenses and cash runway share one row`() {
        assertSame(rowOf("Income / Spend"), rowOf("Cash Runway"))
    }

    @Test
    fun `rows are ordered by theme with income left of expenses and ratio left of runway`() {
        val container = itemView.findViewById<ViewGroup>(R.id.container_metrics_rows)
        val order = listOf("Equity", "Cash to Equity", "Avg. Income", "Income / Spend")
            .map { container.indexOfChild(rowOf(it)) }
        assertEquals(order.sorted(), order)
        assertEquals(4, order.toSet().size)

        assertEquals(0, cellIndexOf("Avg. Income"))
        assertEquals(1, cellIndexOf("Mo. Spend"))
        assertEquals(0, cellIndexOf("Income / Spend"))
        assertEquals(1, cellIndexOf("Cash Runway"))
    }

    @Test
    fun `avg income shows the compact base-currency amount`() {
        val row = rowOf("Avg. Income")
        val values = ArrayList<View>()
        row.findViewsWithText(values, "150", View.FIND_VIEWS_WITH_TEXT)
        assertEquals(1, values.size)
    }
}
