package dev.fitiavana.accounting.ui.home

import android.content.Context
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.p2pprices.P2pPrices
import dev.fitiavana.accounting.network.p2p.P2pAd
import dev.fitiavana.accounting.network.p2p.P2pPaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class HomeP2pPricesAdapterTest {

    private lateinit var adapter: HomeP2pPricesAdapter
    private lateinit var holder: HomeP2pPricesAdapter.ViewHolder
    private val itemView: View get() = holder.itemView
    private var filterClicks = 0

    @Before
    fun setUp() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Accounting)
        adapter = HomeP2pPricesAdapter { filterClicks++ }
        holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
    }

    private fun ads(vararg prices: Double) = prices.map { P2pAd(it, minLimit = 1_000, maxLimit = 2_000, advertiser = "n${it.toInt()}") }

    private fun text(id: Int) = itemView.findViewById<TextView>(id).text.toString()

    private fun submit(prices: P2pPrices?) {
        adapter.submit(prices)
        adapter.onBindViewHolder(holder, 0)
    }

    @Test
    fun `header names the source and pair`() {
        assertEquals("Binance P2P · USDT/MGA", text(R.id.text_p2p_header))
    }

    @Test
    fun `is a single row`() {
        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `filter label says all methods when unfiltered`() {
        assertEquals("All methods ▾", text(R.id.text_p2p_filter))
        submit(P2pPrices(buy = ads(1.0), sell = ads(1.0)))
        assertEquals("All methods ▾", text(R.id.text_p2p_filter))
    }

    @Test
    fun `filter label shows the selected method`() {
        submit(P2pPrices(buy = ads(1.0), sell = ads(1.0), filter = P2pPaymentMethod("Mvola", "Mvola")))

        assertEquals("Mvola ▾", text(R.id.text_p2p_filter))
    }

    @Test
    fun `tapping the filter label calls the listener`() {
        itemView.findViewById<View>(R.id.text_p2p_filter).performClick()

        assertEquals(1, filterClicks)
    }

    @Test
    fun `buy prices on the left and sell prices on the right`() {
        submit(P2pPrices(buy = ads(4600.0, 4601.5, 4602.0), sell = ads(4500.0, 4499.0, 4498.25)))

        assertEquals("Buy USDT", text(R.id.text_p2p_buy_title))
        assertEquals("Sell USDT", text(R.id.text_p2p_sell_title))
        assertEquals("4,600", text(R.id.text_p2p_buy_1))
        assertEquals("4,602", text(R.id.text_p2p_buy_2))
        assertEquals("4,602", text(R.id.text_p2p_buy_3))
        assertEquals("4,500", text(R.id.text_p2p_sell_1))
        assertEquals("4,499", text(R.id.text_p2p_sell_2))
        assertEquals("4,498", text(R.id.text_p2p_sell_3))
    }

    @Test
    fun `missing ads show a placeholder`() {
        submit(P2pPrices(buy = ads(4600.0), sell = null))

        assertEquals("4,600", text(R.id.text_p2p_buy_1))
        assertEquals("–", text(R.id.text_p2p_buy_2))
        assertEquals("–", text(R.id.text_p2p_buy_3))
        assertEquals("–", text(R.id.text_p2p_sell_1))
    }

    @Test
    fun `before any data every slot shows a placeholder`() {
        assertEquals("–", text(R.id.text_p2p_buy_1))
        assertEquals("–", text(R.id.text_p2p_sell_3))
    }

    @Test
    fun `each line shows the advertiser and the limits beside its price`() {
        submit(P2pPrices(buy = ads(4600.0, 4601.5, 4602.0), sell = ads(4500.0, 4499.0, 4498.25)))

        assertEquals("n4600", text(R.id.text_p2p_buy_1_name))
        assertEquals("1.00K–2.00K", text(R.id.text_p2p_buy_1_limits))
        assertEquals("n4601", text(R.id.text_p2p_buy_2_name))
        assertEquals("n4602", text(R.id.text_p2p_buy_3_name))
        assertEquals("n4499", text(R.id.text_p2p_sell_2_name))
        assertEquals("1.00K–2.00K", text(R.id.text_p2p_sell_3_limits))
    }

    @Test
    fun `lines without an ad have no name or limits`() {
        submit(P2pPrices(buy = ads(4600.0), sell = null))

        assertEquals("", text(R.id.text_p2p_buy_2_name))
        assertEquals("", text(R.id.text_p2p_buy_2_limits))
        assertEquals("", text(R.id.text_p2p_sell_1_name))
        assertEquals("", text(R.id.text_p2p_sell_1_limits))
    }

    @Test
    fun `price name and limits share one line, only the name may be cut`() {
        val price = itemView.findViewById<View>(R.id.text_p2p_buy_1)
        val name = itemView.findViewById<TextView>(R.id.text_p2p_buy_1_name)
        val limits = itemView.findViewById<TextView>(R.id.text_p2p_buy_1_limits)

        assertEquals(price.parent, name.parent)
        assertEquals(price.parent, limits.parent)
        assertEquals(TextUtils.TruncateAt.END, name.ellipsize)
        assertEquals(1, name.maxLines)
        assertNull(limits.ellipsize)
        assertEquals(1, limits.maxLines)
    }

    @Test
    fun `amounts are monospace so digits line up across lines`() {
        for (id in listOf(R.id.text_p2p_buy_1, R.id.text_p2p_sell_3, R.id.text_p2p_buy_2_limits, R.id.text_p2p_sell_1_limits)) {
            val typeface = itemView.findViewById<TextView>(id).typeface
            assertEquals("monospace", shadowOf(typeface).fontDescription.familyName)
        }
    }
}
