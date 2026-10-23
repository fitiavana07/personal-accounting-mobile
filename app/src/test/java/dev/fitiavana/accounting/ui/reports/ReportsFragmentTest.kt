package dev.fitiavana.accounting.ui.reports

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `trend charts are shown above the report and the period selector stays pinned outside the scroll`() {
        val activity = launch()

        assertNotNull(activity.findViewById<View>(R.id.chart_net_worth))
        assertNotNull(activity.findViewById<View>(R.id.chart_net_income))
        val scrollContent = activity.findViewById<ViewGroup>(R.id.layout_reports_scroll_content)
        val trend = activity.findViewById<View>(R.id.layout_trend)
        val report = activity.findViewById<View>(R.id.recycler_reports_content)
        assertTrue(scrollContent.indexOfChild(trend) < scrollContent.indexOfChild(report))
        val header = activity.findViewById<View>(R.id.layout_reports_header)
        val scroll = activity.findViewById<View>(R.id.scroll_reports)
        assertTrue(header.parent === scroll.parent)
        assertTrue(activity.findViewById<View>(R.id.text_reports_year).parent.parent === header)
    }

    @Test
    fun `a loading indicator sits in the same container as the trend charts`() {
        val activity = launch()

        val trend = activity.findViewById<ViewGroup>(R.id.layout_trend)
        val progress = activity.findViewById<View>(R.id.progress_trend)
        val charts = activity.findViewById<View>(R.id.layout_trend_charts)
        assertEquals(trend, progress.parent)
        assertEquals(trend, charts.parent)
    }

    @Test
    fun `tapping the trend charts block does not hide the charts`() {
        val activity = launch()

        activity.findViewById<View>(R.id.layout_trend_charts).performClick()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.chart_net_worth).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.chart_net_income).visibility)
    }

    @Test
    fun `the trend block is visible by default and tapping the title collapses then expands it`() {
        val activity = launch()
        val trend = activity.findViewById<View>(R.id.layout_trend)
        val toggle = activity.findViewById<android.widget.TextView>(R.id.text_trend_toggle)
        assertEquals(View.VISIBLE, trend.visibility)
        assertTrue(toggle.text.endsWith("▼"))

        toggle.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, trend.visibility)
        assertTrue(toggle.text.endsWith("►"))

        toggle.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, trend.visibility)
    }

    @Test
    fun `the screen has no FAB of its own`() {
        val activity = launch()

        assertNull(activity.findViewById<View>(R.id.fab_add_transaction))
    }
}
