package dev.fitiavana.accounting.ui.common

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class EquityStatementAdapterTest {

    private val display = EquityStatementDisplay(
        columnTitles = listOf("Unclosed IS Accounts", "Total"),
        rows = listOf(
            EquityStatementDisplayRow("Balance at Sep 30", listOf("6,603,000", "6,603,000"), emphasized = true),
            EquityStatementDisplayRow("Changes in Drawing", listOf("-", "-")),
            EquityStatementDisplayRow("Changes in Unclosed IS Accounts", listOf("3,935,000", "3,935,000")),
            EquityStatementDisplayRow("Balance at Oct 23", listOf("10,538,000", "10,538,000"), emphasized = true)
        )
    )

    // Position 0 is the column-titles header; data rows follow.
    private fun bindRow(position: Int): View {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val parent = RecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
        val adapter = EquityStatementAdapter()
        adapter.submitList(display)
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(position))
        adapter.onBindViewHolder(holder, position)
        return holder.itemView
    }

    private fun cells(row: View): List<TextView> {
        val content = row.findViewById<LinearLayout>(R.id.content_equity_row)
        return (0 until content.childCount).map { content.getChildAt(it) as TextView }
    }

    private fun background(row: View) = row.findViewById<View>(R.id.layout_equity_row).background

    @Test
    fun `the table is one card with a distinct top, tinted balance rows, plain changes rows and a distinct bottom`() {
        val backgrounds = (0..4).map { background(bindRow(it)) }

        backgrounds.forEach { assertNotNull(it) }
        val top = backgrounds[0].constantState
        val openingBalance = backgrounds[1].constantState
        val change = backgrounds[2].constantState
        val bottom = backgrounds[4].constantState
        assertEquals(4, setOf(top, openingBalance, change, bottom).size)
        assertEquals(change, backgrounds[3].constantState)
    }

    @Test
    fun `numbers use a proportional font with tabular figures instead of monospace`() {
        val value = cells(bindRow(2))[1]

        assertNotEquals(Typeface.MONOSPACE, value.typeface)
        assertEquals("tnum", value.fontFeatureSettings)
    }

    @Test
    fun `column titles are muted while data cells use the primary text colour`() {
        val title = cells(bindRow(0))[1]
        val value = cells(bindRow(3))[1]

        assertNotEquals(value.currentTextColor, title.currentTextColor)
        assertEquals("Unclosed IS Accounts", title.text.toString())
    }

    @Test
    fun `a dash cell is muted like a column title and unlike a real amount`() {
        val dash = cells(bindRow(2))[1]
        val amount = cells(bindRow(3))[1]
        val title = cells(bindRow(0))[1]

        assertEquals("-", dash.text.toString())
        assertNotEquals(amount.currentTextColor, dash.currentTextColor)
        assertEquals(title.currentTextColor, dash.currentTextColor)
    }
}
