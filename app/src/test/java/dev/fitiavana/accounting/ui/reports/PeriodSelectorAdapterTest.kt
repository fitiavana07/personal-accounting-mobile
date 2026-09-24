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

    @Test
    fun `icon is hidden when no iconFor is provided`() {
        val adapter = PeriodSelectorAdapter<Int>(labelFor = { it.toString() }, onSelected = {})
        adapter.submitList(listOf(2024), 2024)
        val holder = inflateHolder(adapter)
        adapter.onBindViewHolder(holder as PeriodSelectorAdapter<Int>.ViewHolder, 0)

        val icon = holder.itemView.findViewById<ImageView>(R.id.image_period_selector)
        assertEquals(View.GONE, icon.visibility)
    }

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
}
