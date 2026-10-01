package dev.fitiavana.accounting.ui.cexprices

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.cexprices.CexPrice
import dev.fitiavana.accounting.network.cex.CexId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class CexPricesAdapterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun bind(adapter: CexPricesAdapter, position: Int): Pair<String, String> {
        val parent: ViewGroup = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, position)
        return holder.itemView.findViewById<TextView>(R.id.text_cex_name).text.toString() to
            holder.itemView.findViewById<TextView>(R.id.text_cex_price).text.toString()
    }

    @Test
    fun `one row per submitted price`() {
        val adapter = CexPricesAdapter()
        adapter.submit(listOf(CexPrice(CexId.BINANCE, 1.0, null), CexPrice(CexId.KRAKEN, 2.0, null)))

        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `row shows exchange name and formatted price`() {
        val adapter = CexPricesAdapter()
        adapter.submit(listOf(CexPrice(CexId.BINANCE, 65000.5, null)))

        assertEquals("Binance" to "65,000.50", bind(adapter, 0))
    }

    @Test
    fun `row without price shows unavailable text`() {
        val adapter = CexPricesAdapter()
        adapter.submit(listOf(CexPrice(CexId.OKX, null, IOException("x"))))

        assertEquals("OKX" to context.getString(R.string.cex_price_unavailable), bind(adapter, 0))
    }

    @Test
    fun `submitting null clears rows`() {
        val adapter = CexPricesAdapter()
        adapter.submit(listOf(CexPrice(CexId.BINANCE, 1.0, null)))
        adapter.submit(null)

        assertEquals(0, adapter.itemCount)
    }
}
