package dev.fitiavana.accounting.ui.alerts

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.alerts.AlertTypes
import dev.fitiavana.accounting.features.alerts.GainLossAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AlertsAdapterTest {

    private fun item(id: String, threshold: Double) = AlertItem(
        GainLossAlert(id, "acc", AlertTypes.CROSSING_BELOW, threshold, 0L),
        "Account"
    )

    @Test
    fun wrapContentRecyclerViewShowsEveryAlert() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.setTheme(R.style.Theme_Accounting)
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val adapter = AlertsAdapter(onClick = {}, onDelete = {})
        recycler.adapter = adapter
        val parent = FrameLayout(context)
        parent.addView(recycler)

        adapter.submit(listOf(item("1", -5.0), item("2", -10.0), item("3", -15.0)))
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)

        assertEquals(3, recycler.childCount)
    }

    @Test
    fun homeDetailAlertsContainerShowsEveryCardAtLargeFontScale() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val context = base.createConfigurationContext(
            android.content.res.Configuration(base.resources.configuration).apply { fontScale = 1.25f }
        )
        context.setTheme(R.style.Theme_Accounting)
        val root = android.view.LayoutInflater.from(context).inflate(R.layout.activity_home_detail, null)
        // A wrap_content RecyclerView here was measured too short at 125% and hid every alert after the first.
        val container = root.findViewById<View>(R.id.container_detail_alerts) as LinearLayout
        listOf(item("1", -5.0), item("2", -10.0), item("3", -15.0)).forEach {
            val holder = AlertsAdapter.ViewHolder.inflate(container)
            holder.bind(it, onClick = {}, onDelete = {})
            container.addView(holder.itemView)
        }

        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1080, 1920)

        assertEquals(3, container.childCount)
        container.children().forEach { assertTrue(it.height > 0) }
    }

    private fun LinearLayout.children() = (0 until childCount).map { getChildAt(it) }

    @Test
    fun viewHolderBindsAlertWithoutRecyclerView() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.setTheme(R.style.Theme_Accounting)
        val holder = AlertsAdapter.ViewHolder.inflate(FrameLayout(context))
        var clicked: AlertItem? = null

        holder.bind(item("1", -5.0), onClick = { clicked = it }, onDelete = {})
        holder.itemView.performClick()

        assertEquals("1", clicked?.alert?.id)
        assertEquals(
            context.getString(R.string.alert_item_below, "-5.0%"),
            holder.itemView.findViewById<android.widget.TextView>(R.id.text_alert_condition).text.toString()
        )
    }
}
