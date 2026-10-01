package dev.fitiavana.accounting.ui.home

import android.content.Context
import android.view.View
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
                incomeToExpensesPercent = 120
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

    @Test
    fun `equity and cash share one row`() {
        assertSame(rowOf("Equity"), rowOf("Cash"))
    }

    @Test
    fun `cash to equity and income to expenses share one row`() {
        assertSame(rowOf("Cash to Equity"), rowOf("Income to Expenses"))
    }

    @Test
    fun `remaining metrics keep their own rows`() {
        val rows = setOf(
            rowOf("Equity"),
            rowOf("Cash to Equity"),
            rowOf("Monthly Expenses"),
            rowOf("Cash to Emergency Fund"),
            rowOf("Cash Runway")
        )
        assertEquals(5, rows.size)
    }
}
