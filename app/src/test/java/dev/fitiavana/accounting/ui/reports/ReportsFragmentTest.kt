package dev.fitiavana.accounting.ui.reports

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.transactions.AddTransactionActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ReportsFragmentTest {

    private fun launch(): FragmentActivity {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java)
            .setup()
            .get()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, ReportsFragment())
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    @Test
    fun `trend charts are shown above the period selector`() {
        val activity = launch()

        assertNotNull(activity.findViewById<View>(R.id.chart_net_worth))
        assertNotNull(activity.findViewById<View>(R.id.chart_net_income))
        val content = activity.findViewById<ViewGroup>(R.id.layout_reports_content)
        val charts = activity.findViewById<View>(R.id.layout_trend_charts)
        val years = activity.findViewById<View>(R.id.recycler_reports_years)
        assertTrue(content.indexOfChild(charts) < content.indexOfChild(years))
    }

    @Test
    fun `tapping the trend charts block does not hide the charts`() {
        val activity = launch()

        activity.findViewById<View>(R.id.layout_trend_charts).performClick()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.chart_net_worth).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.chart_net_income).visibility)
    }

    @Test
    fun `tapping the FAB opens AddTransactionActivity`() {
        val activity = launch()

        activity.findViewById<View>(R.id.fab_add_transaction).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(
            AddTransactionActivity::class.java.name,
            started.component?.className
        )
    }
}
