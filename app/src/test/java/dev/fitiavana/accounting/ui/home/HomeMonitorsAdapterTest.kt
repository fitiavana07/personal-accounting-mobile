package dev.fitiavana.accounting.ui.home

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitor
import dev.fitiavana.accounting.features.chainmonitors.MonitorRow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class HomeMonitorsAdapterTest {

    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Accounting)

    private fun row(label: String, balance: String?, symbol: String = "USDC", checkedAt: Long? = null) = MonitorRow(
        ChainMonitor("id-$label", label, "0x" + "a".repeat(40), "0x" + "b".repeat(40), symbol, 6, "0", 15, 1L),
        balance,
        checkedAt
    )

    private fun updated(view: View): List<String> {
        val container = view.findViewById<ViewGroup>(R.id.container_home_monitors_rows)
        return (0 until container.childCount).map {
            container.getChildAt(it).findViewById<TextView>(R.id.text_monitor_row_updated).text.toString()
        }
    }

    private fun bound(rows: List<MonitorRow>, onClick: () -> Unit = {}): View {
        val adapter = HomeMonitorsAdapter(onClick)
        adapter.submit(rows)
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        return holder.itemView
    }

    private fun lines(view: View): List<Pair<String, String>> {
        val container = view.findViewById<ViewGroup>(R.id.container_home_monitors_rows)
        return (0 until container.childCount).map {
            val line = container.getChildAt(it)
            line.findViewById<TextView>(R.id.text_monitor_row_label).text.toString() to
                line.findViewById<TextView>(R.id.text_monitor_row_amount).text.toString()
        }
    }

    @Test
    fun `the block is hidden when there is no monitor`() {
        val adapter = HomeMonitorsAdapter {}

        assertEquals(0, adapter.itemCount)
        adapter.submit(emptyList())
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `the block is shown once there are monitors`() {
        val adapter = HomeMonitorsAdapter {}

        adapter.submit(listOf(row("Vault", "1")))

        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `each monitor shows its label and last balance with the token`() {
        val view = bound(listOf(row("Acres 40 available", "1234.5"), row("Other", "0", "DAI")))

        assertEquals(
            listOf("Acres 40 available" to "1,234.5 USDC", "Other" to "0 DAI"),
            lines(view)
        )
    }

    @Test
    fun `a monitor without a reading shows a dash`() {
        val view = bound(listOf(row("Vault", null)))

        assertEquals(listOf("Vault" to "–"), lines(view))
    }

    @Test
    fun `a reading from today shows its time of day`() {
        val at = System.currentTimeMillis()
        val view = bound(listOf(row("Vault", "1", checkedAt = at)))

        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(at))
        assertEquals(listOf("Updated $time"), updated(view))
    }

    @Test
    fun `an older reading shows its date and time`() {
        val at = System.currentTimeMillis() - 3 * 24 * 3_600_000L
        val view = bound(listOf(row("Vault", "1", checkedAt = at)))

        val text = java.text.DateFormat
            .getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
            .format(java.util.Date(at))
        assertEquals(listOf("Updated $text"), updated(view))
    }

    @Test
    fun `a monitor never read says so`() {
        val view = bound(listOf(row("Vault", null)))

        assertEquals(listOf("Not fetched yet"), updated(view))
    }

    @Test
    fun `rebinding replaces the previous lines`() {
        val adapter = HomeMonitorsAdapter {}
        adapter.submit(listOf(row("A", "1"), row("B", "2")))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)

        adapter.submit(listOf(row("C", "3")))
        adapter.onBindViewHolder(holder, 0)

        assertEquals(listOf("C" to "3 USDC"), lines(holder.itemView))
    }

    @Test
    fun `tapping the header opens the monitors screen`() {
        var clicks = 0
        val view = bound(listOf(row("Vault", "1"))) { clicks++ }

        view.findViewById<View>(R.id.text_home_monitors_header).performClick()

        assertEquals(1, clicks)
    }
}
