package dev.fitiavana.accounting.ui.common

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    fun `amounts are trimmed and non-parenthesized ones reserve the closing paren width`() {
        val adapter = ReportAdapter(onAccountClick = {})
        val plain = bind(adapter, listOf(ReportDisplayRow.NativeLine("USDT", "1,250.5 ")), 0)
        val contra = bind(adapter, listOf(ReportDisplayRow.NativeLine("USDT", "(1,250.5)")), 0)

        assertEquals("1,250.5", amount(plain).text.toString())
        assertTrue(amount(plain).paddingEnd > 0)
        assertEquals("(1,250.5)", amount(contra).text.toString())
        assertEquals(0, amount(contra).paddingEnd)
    }

    @Test
    fun `expandable account line shows a collapsed chevron and reports clicks with its account id`() {
        var clicked: String? = null
        val adapter = ReportAdapter(onAccountClick = { clicked = it })
        val h = bind(
            adapter,
            listOf(ReportDisplayRow.AccountLine("Bybit", "50,000 ", accountId = "a1", expandable = true)),
            0
        )

        assertEquals("Bybit ►", label(h).text.toString())
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

        assertEquals("Bybit ▼", label(h).text.toString())
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
        // The trailing alignment space is dropped; the column alignment comes from the view's end padding.
        assertEquals("1,250.5", amount(h).text.toString())
        assertFalse(h.itemView.isClickable)
    }

    @Test
    fun `APR line shows APR on the left and the rate on the right`() {
        val adapter = ReportAdapter()
        val h = bind(adapter, listOf(ReportDisplayRow.AprLine("5.5% ", "a1", 5.5, "Earn")), 0)

        assertEquals("APR", label(h).text.toString())
        assertEquals("5.5%", amount(h).text.toString())
        assertFalse(h.itemView.isClickable)
        assertFalse(label(h).isClickable)
    }

    @Test
    fun `APR value is underlined and reports clicks with its row`() {
        var clicked: ReportDisplayRow.AprLine? = null
        val adapter = ReportAdapter(onAprClick = { clicked = it })
        val h = bind(adapter, listOf(ReportDisplayRow.AprLine("5.5% ", "a1", 5.5, "Earn")), 0)

        val text = amount(h).text as android.text.Spanned
        val underline = text.getSpans(0, text.length, android.text.style.UnderlineSpan::class.java)
        assertEquals(1, underline.size)
        assertEquals(0, text.getSpanStart(underline[0]))
        assertEquals(4, text.getSpanEnd(underline[0]))
        amount(h).performClick()
        assertEquals(ReportDisplayRow.AprLine("5.5% ", "a1", 5.5, "Earn"), clicked)
    }

    @Test
    fun `APR value is not tappable without a handler`() {
        val adapter = ReportAdapter()
        val h = bind(adapter, listOf(ReportDisplayRow.AprLine("5.5% ", "a1", 5.5, "Earn")), 0)

        assertFalse(amount(h).isClickable)
    }

    @Test
    fun `recycled holder loses the APR underline and click when rebound`() {
        var clicked = false
        val adapter = ReportAdapter(onAprClick = { clicked = true })
        val rows = listOf(
            ReportDisplayRow.AprLine("5.5% ", "a1", 5.5, "Earn"),
            ReportDisplayRow.NativeLine("USDT", "1 "),
            ReportDisplayRow.AccountLine("Cash", "20,000 ", accountId = "a2")
        )
        val h = bind(adapter, rows, 0)

        for (position in 1..2) {
            adapter.onBindViewHolder(h, position)
            val text = amount(h).text
            assertTrue(text !is android.text.Spanned ||
                text.getSpans(0, text.length, android.text.style.UnderlineSpan::class.java).isEmpty())
            assertFalse(amount(h).isClickable)
            amount(h).performClick()
        }
        assertFalse(clicked)
    }

    // --- Card style (Reports screen) ---

    private val section = listOf(
        ReportDisplayRow.SectionHeader("Assets"),
        ReportDisplayRow.AccountLine("Cash", "100 "),
        ReportDisplayRow.TotalLine("Total Assets", "Ar 100 ", emphasized = true)
    )

    private fun marginsOf(h: RecyclerView.ViewHolder) = h.itemView.layoutParams as ViewGroup.MarginLayoutParams

    private fun dp(h: RecyclerView.ViewHolder, value: Int) =
        (value * h.itemView.resources.displayMetrics.density).toInt()

    @Test
    fun `a section runs from its header to its emphasized total as one card`() {
        val rows = section + listOf(
            ReportDisplayRow.SectionHeader("Liabilities"),
            ReportDisplayRow.TotalLine("Total Liabilities", "Ar 0 ", emphasized = true)
        )

        assertEquals(
            listOf(
                ReportAdapter.CardRole.TOP, ReportAdapter.CardRole.MIDDLE, ReportAdapter.CardRole.BOTTOM,
                ReportAdapter.CardRole.TOP, ReportAdapter.CardRole.BOTTOM
            ),
            ReportAdapter.cardRolesFor(rows)
        )
    }

    @Test
    fun `an emphasized total outside any section is a single card and non-emphasized totals stay inside`() {
        val rows = listOf(
            ReportDisplayRow.SectionHeader("Equity"),
            ReportDisplayRow.TotalLine("Total Original Equity", "Ar 1 "),
            ReportDisplayRow.TotalLine("Total Equity", "Ar 1 ", emphasized = true),
            ReportDisplayRow.TotalLine("Net Income", "Ar 5 ", emphasized = true)
        )

        assertEquals(
            listOf(
                ReportAdapter.CardRole.TOP, ReportAdapter.CardRole.MIDDLE,
                ReportAdapter.CardRole.BOTTOM, ReportAdapter.CardRole.SINGLE
            ),
            ReportAdapter.cardRolesFor(rows)
        )
    }

    @Test
    fun `title and date rows are never part of a card`() {
        val rows = listOf(ReportDisplayRow.Title("Balance Sheet"), ReportDisplayRow.DateLine("Balances at X")) + section

        assertEquals(
            listOf(null, null, ReportAdapter.CardRole.TOP, ReportAdapter.CardRole.MIDDLE, ReportAdapter.CardRole.BOTTOM),
            ReportAdapter.cardRolesFor(rows)
        )
    }

    @Test
    fun `card style insets card rows and gives the first row a gap above it`() {
        val adapter = ReportAdapter(cardStyle = true)
        val top = bind(adapter, section, 0)
        val middle = bind(adapter, section, 1)

        assertEquals(dp(top, 16), marginsOf(top).marginStart)
        assertEquals(dp(top, 16), marginsOf(top).marginEnd)
        assertEquals(dp(top, 12), marginsOf(top).topMargin)
        assertEquals(dp(middle, 16), marginsOf(middle).marginStart)
        assertEquals(0, marginsOf(middle).topMargin)
        assertNotNull(top.itemView.findViewById<View>(R.id.layout_report_row).background)
    }

    @Test
    fun `card style uses a different background for the top, middle and bottom of a card`() {
        val adapter = ReportAdapter(cardStyle = true)
        val states = (0..2).map { bind(adapter, section, it).itemView.findViewById<View>(R.id.layout_report_row).background.constantState }

        assertEquals(3, states.toSet().size)
    }

    @Test
    fun `a recycled card row loses its card margins and background when rebound outside a card`() {
        val adapter = ReportAdapter(cardStyle = true)
        val rows = section + ReportDisplayRow.DateLine("x") + ReportDisplayRow.NativeLine("USDT", "1 ")
        val recycled = bind(adapter, rows, 0)
        adapter.onBindViewHolder(recycled, 4)

        assertEquals(0, marginsOf(recycled).marginStart)
        assertEquals(0, marginsOf(recycled).topMargin)
    }

    @Test
    fun `default style keeps rows flat with no margins`() {
        val adapter = ReportAdapter()
        val h = bind(adapter, section, 0)

        assertEquals(0, marginsOf(h).marginStart)
        assertEquals(0, marginsOf(h).topMargin)
    }

    @Test
    fun `card style tints an emphasized net result green or red by its sign`() {
        val adapter = ReportAdapter(cardStyle = true)
        val gain = bind(adapter, listOf(ReportDisplayRow.TotalLine("Net Income", "Ar 5 ", true, netResultSign = 1)), 0)
        val loss = bind(adapter, listOf(ReportDisplayRow.TotalLine("Net Loss", "(Ar 5)", true, netResultSign = -1)), 0)
        val context = gain.itemView.context

        assertEquals(ContextCompat.getColor(context, R.color.report_net_gain), amount(gain).currentTextColor)
        assertEquals(ContextCompat.getColor(context, R.color.report_net_loss), amount(loss).currentTextColor)
    }

    @Test
    fun `card style leaves ordinary totals, subtotals and a zero net result untinted`() {
        val adapter = ReportAdapter(cardStyle = true)
        val plain = bind(adapter, listOf(ReportDisplayRow.TotalLine("Total Assets", "Ar 5 ", true)), 0)
        val subtotal = bind(adapter, listOf(ReportDisplayRow.TotalLine("Total Unclosed IS", "Ar 5 ", false, netResultSign = 1)), 0)
        val context = plain.itemView.context
        val gain = ContextCompat.getColor(context, R.color.report_net_gain)
        val loss = ContextCompat.getColor(context, R.color.report_net_loss)

        assertEquals(amount(plain).currentTextColor, label(plain).currentTextColor)
        assertEquals(amount(subtotal).currentTextColor, label(subtotal).currentTextColor)
        assertFalse(amount(plain).currentTextColor in listOf(gain, loss))
    }

    @Test
    fun `default style never tints a net result`() {
        val adapter = ReportAdapter()
        val h = bind(adapter, listOf(ReportDisplayRow.TotalLine("Net Income", "Ar 5 ", true, netResultSign = 1)), 0)

        assertEquals(label(h).currentTextColor, amount(h).currentTextColor)
    }
}
