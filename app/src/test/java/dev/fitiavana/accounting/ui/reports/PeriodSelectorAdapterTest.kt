package dev.fitiavana.accounting.ui.reports

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class PeriodSelectorAdapterTest {

    private fun inflateHolder(adapter: PeriodSelectorAdapter<*>): RecyclerView.ViewHolder {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val parent = RecyclerView(activity)
        parent.layoutManager = LinearLayoutManager(activity)
        return adapter.onCreateViewHolder(parent, 0)
    }

    // inflateHolder returns the raw RecyclerView.ViewHolder; the adapter's type argument is erased at runtime.
    @Suppress("UNCHECKED_CAST")
    @Test
    fun `icon is hidden when no iconFor is provided`() {
        val adapter = PeriodSelectorAdapter<Int>(labelFor = { it.toString() }, onSelected = {})
        adapter.submitList(listOf(2024), 2024)
        val holder = inflateHolder(adapter)
        adapter.onBindViewHolder(holder as PeriodSelectorAdapter<Int>.ViewHolder, 0)

        val icon = holder.itemView.findViewById<ImageView>(R.id.image_period_selector)
        assertEquals(View.GONE, icon.visibility)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `icon is visible with the resource from iconFor when provided`() {
        val adapter = PeriodSelectorAdapter<ReportType>(
            labelFor = { it.label },
            onSelected = {},
            iconFor = { R.drawable.ic_report_balance_sheet }
        )
        adapter.submitList(listOf(ReportType.BALANCE_SHEET), ReportType.BALANCE_SHEET)
        val holder = inflateHolder(adapter)
        adapter.onBindViewHolder(holder as PeriodSelectorAdapter<ReportType>.ViewHolder, 0)

        val icon = holder.itemView.findViewById<ImageView>(R.id.image_period_selector)
        assertEquals(View.VISIBLE, icon.visibility)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `layoutRes selects the item layout`() {
        val adapter = PeriodSelectorAdapter<Int>(
            labelFor = { it.toString() },
            onSelected = {},
            layoutRes = R.layout.item_period_compact
        )
        adapter.submitList(listOf(2024), 2024)
        val holder = inflateHolder(adapter)
        adapter.onBindViewHolder(holder as PeriodSelectorAdapter<Int>.ViewHolder, 0)

        assertEquals("2024", holder.itemView.findViewById<android.widget.TextView>(R.id.text_period_selector).text.toString())
    }

    @Test
    fun `submitList also brings the item after the selected one into view`() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val recycler = RecyclerView(activity)
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        recycler.layoutManager = layoutManager
        val adapter = PeriodSelectorAdapter<Int>(labelFor = { it.toString() }, onSelected = {})
        recycler.adapter = adapter
        fun layout() {
            recycler.measure(
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
            )
            recycler.layout(0, 0, 300, 100)
        }
        layout()

        adapter.submitList((0..11).toList(), 9)
        layout()

        val next = layoutManager.findViewByPosition(10)
        assertEquals(true, next != null && next.right <= 300 && next.left >= 0)
    }

    @Test
    fun `submitList with the last item selected does not scroll out of range`() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val recycler = RecyclerView(activity)
        recycler.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        val adapter = PeriodSelectorAdapter<Int>(labelFor = { it.toString() }, onSelected = {})
        recycler.adapter = adapter

        adapter.submitList((0..11).toList(), 11)

        assertEquals(12, adapter.itemCount)
    }

    @Test
    fun `submitList scrolls the selected item fully into view`() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val recycler = RecyclerView(activity)
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        recycler.layoutManager = layoutManager
        val adapter = PeriodSelectorAdapter<Int>(labelFor = { it.toString() }, onSelected = {})
        recycler.adapter = adapter
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
        )
        recycler.layout(0, 0, 300, 100)

        adapter.submitList((0..11).toList(), 9)
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
        )
        recycler.layout(0, 0, 300, 100)

        val selectedView = layoutManager.findViewByPosition(9)
        assertEquals(true, selectedView != null && selectedView.right <= 300 && selectedView.left >= 0)
    }
}
