package dev.fitiavana.accounting.ui.chainmonitors

import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ChainMonitorsAdapterTest {

    private val monitor = ChainMonitor(
        "m", "Acres 40 available", "0xB99B6dF96d4d5448cC0a5B3e0ef7896df9507Cf5",
        "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", "USDC", 6, "1000.5", 30, 1L
    )

    private fun bind(clicked: MutableList<ChainMonitor> = mutableListOf(), deleted: MutableList<ChainMonitor> = mutableListOf()): ChainMonitorsAdapter.ViewHolder {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Accounting)
        val adapter = ChainMonitorsAdapter(onClick = { clicked += it }, onDelete = { deleted += it })
        adapter.submit(listOf(monitor))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        return holder
    }

    @Test
    fun `a card shows the label, the condition and a shortened holder address`() {
        val view = bind().itemView

        assertEquals("Acres 40 available", view.findViewById<TextView>(R.id.text_monitor_label).text.toString())
        val condition = view.findViewById<TextView>(R.id.text_monitor_condition).text.toString()
        assertTrue(condition, condition.contains("1,000.5 USDC"))
        assertTrue(condition, condition.contains("30"))
        assertEquals("0xB99B…7Cf5", view.findViewById<TextView>(R.id.text_monitor_holder).text.toString())
    }

    @Test
    fun `tapping the card edits and the bin deletes`() {
        val clicked = mutableListOf<ChainMonitor>()
        val deleted = mutableListOf<ChainMonitor>()
        val view = bind(clicked, deleted).itemView

        view.performClick()
        view.findViewById<android.view.View>(R.id.btn_delete_monitor).performClick()

        assertEquals(listOf(monitor), clicked)
        assertEquals(listOf(monitor), deleted)
    }
}
