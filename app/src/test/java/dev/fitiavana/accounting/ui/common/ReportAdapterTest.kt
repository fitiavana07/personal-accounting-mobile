package dev.fitiavana.accounting.ui.common

import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ReportAdapterTest {

    private fun bind(
        adapter: ReportAdapter,
        rows: List<ReportDisplayRow>,
        position: Int,
        holder: RecyclerView.ViewHolder? = null
    ): RecyclerView.ViewHolder {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val parent = RecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
        adapter.submitList(rows)
        val h = holder ?: adapter.onCreateViewHolder(parent, adapter.getItemViewType(position))
        adapter.onBindViewHolder(h, position)
        return h
    }

    private fun label(h: RecyclerView.ViewHolder) = h.itemView.findViewById<TextView>(R.id.text_report_label)
    private fun amount(h: RecyclerView.ViewHolder) = h.itemView.findViewById<TextView>(R.id.text_report_amount)

    @Test
    fun `expandable account line shows a collapsed chevron and reports clicks with its account id`() {
        var clicked: String? = null
        val adapter = ReportAdapter(onAccountClick = { clicked = it })
        val h = bind(
            adapter,
            listOf(ReportDisplayRow.AccountLine("Bybit", "50,000 ", accountId = "a1", expandable = true)),
            0
        )

        assertEquals("Bybit ▸", label(h).text.toString())
        h.itemView.performClick()
        assertEquals("a1", clicked)
    }

    @Test
    fun `expanded account line shows an expanded chevron`() {
        val adapter = ReportAdapter(onAccountClick = {})
        val h = bind(
            adapter,
            listOf(ReportDisplayRow.AccountLine("Bybit", "50,000 ", accountId = "a1", expandable = true, expanded = true)),
            0
        )

        assertEquals("Bybit ▾", label(h).text.toString())
    }

    @Test
    fun `non-expandable account line has no chevron and is not clickable`() {
        var clicked = false
        val adapter = ReportAdapter(onAccountClick = { clicked = true })
        val h = bind(adapter, listOf(ReportDisplayRow.AccountLine("Cash", "20,000 ", accountId = "a2")), 0)

        assertEquals("Cash", label(h).text.toString())
        assertFalse(h.itemView.isClickable)
        h.itemView.performClick()
        assertFalse(clicked)
    }

    @Test
    fun `recycled holder resets click state when rebound to a non-expandable line`() {
        val adapter = ReportAdapter(onAccountClick = {})
        val rows = listOf(
            ReportDisplayRow.AccountLine("Bybit", "50,000 ", accountId = "a1", expandable = true),
            ReportDisplayRow.AccountLine("Cash", "20,000 ", accountId = "a2")
        )
        val h = bind(adapter, rows, 0)
        assertTrue(h.itemView.isClickable)

        adapter.onBindViewHolder(h, 1)

        assertFalse(h.itemView.isClickable)
        assertEquals("Cash", label(h).text.toString())
    }

    @Test
    fun `account line is not clickable without a click handler`() {
        val adapter = ReportAdapter()
        val h = bind(
            adapter,
            listOf(ReportDisplayRow.AccountLine("Bybit", "50,000 ", accountId = "a1", expandable = true)),
            0
        )

        assertFalse(h.itemView.isClickable)
    }

    @Test
    fun `native line shows the instrument code on the left and the amount on the right`() {
        val adapter = ReportAdapter()
        val h = bind(adapter, listOf(ReportDisplayRow.NativeLine("USDT", "1,250.5 ")), 0)

        assertEquals("USDT", label(h).text.toString())
        assertEquals("1,250.5 ", amount(h).text.toString())
        assertFalse(h.itemView.isClickable)
    }

    @Test
    fun `APR line shows APR on the left and the rate on the right`() {
        val adapter = ReportAdapter()
        val h = bind(adapter, listOf(ReportDisplayRow.AprLine("5.5% ")), 0)

        assertEquals("APR", label(h).text.toString())
        assertEquals("5.5% ", amount(h).text.toString())
        assertFalse(h.itemView.isClickable)
    }
}
