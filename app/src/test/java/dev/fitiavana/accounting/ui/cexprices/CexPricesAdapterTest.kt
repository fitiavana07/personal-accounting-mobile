package dev.fitiavana.accounting.ui.cexprices

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.network.cex.CexId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class CexPricesAdapterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun row(
        cex: CexId,
        price: Double?,
        diff: Double? = null,
        lowest: Boolean = false,
        highest: Boolean = false
    ) = CexPriceRow(cex, price, diff, lowest, highest)

    private fun bind(adapter: CexPricesAdapter, position: Int): Triple<String, String, String> {
        val parent: ViewGroup = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, position)
        val v = holder.itemView
        return Triple(
            v.findViewById<TextView>(R.id.text_cex_name).text.toString(),
            v.findViewById<TextView>(R.id.text_cex_price).text.toString(),
            v.findViewById<TextView>(R.id.text_cex_detail).text.toString()
        )
    }

    @Test
    fun `one row per submitted item`() {
        val adapter = CexPricesAdapter()
        adapter.submit(listOf(row(CexId.BINANCE, 1.0), row(CexId.KRAKEN, 2.0)))

        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `row shows exchange name and formatted price`() {
        val adapter = CexPricesAdapter()
        adapter.submit(listOf(row(CexId.BINANCE, 65000.5, diff = 0.5)))

        val (name, price, _) = bind(adapter, 0)

        assertEquals("Binance", name)
        assertEquals("65,000.50", price)
    }

    @Test
    fun `lowest row is tagged and others show the signed difference`() {
        val adapter = CexPricesAdapter()
        adapter.submit(
            listOf(
                row(CexId.BYBIT, 100.0, diff = 0.0, lowest = true),
                row(CexId.OKX, 101.234, diff = 1.234)
            )
        )

        assertEquals(context.getString(R.string.cex_tag_lowest), bind(adapter, 0).third)
        assertEquals("+1.23%", bind(adapter, 1).third)
    }

    @Test
    fun `highest row shows difference and tag`() {
        val adapter = CexPricesAdapter()
        adapter.submit(listOf(row(CexId.OKX, 102.0, diff = 2.0, highest = true)))

        assertEquals("+2.00% · " + context.getString(R.string.cex_tag_highest), bind(adapter, 0).third)
    }

    @Test
    fun `row without price shows unavailable text and no detail`() {
        val adapter = CexPricesAdapter()
        adapter.submit(listOf(row(CexId.OKX, null)))

        val (name, price, detail) = bind(adapter, 0)

        assertEquals("OKX", name)
        assertEquals(context.getString(R.string.cex_price_unavailable), price)
        assertEquals("", detail)
    }

    @Test
    fun `submitting an empty list clears rows`() {
        val adapter = CexPricesAdapter()
        adapter.submit(listOf(row(CexId.BINANCE, 1.0)))
        adapter.submit(emptyList())

        assertEquals(0, adapter.itemCount)
    }
}
