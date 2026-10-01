package dev.fitiavana.accounting.ui.home

import android.content.Context
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    private fun ads(vararg prices: Double) = prices.map { P2pAd(it, advertiser = "n$it") }

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
    fun `each line shows the formatted ad details beside its price`() {
        submit(P2pPrices(buy = ads(4600.0, 4601.5, 4602.0), sell = ads(4500.0, 4499.0, 4498.25)))

        assertEquals("n4600.0", text(R.id.text_p2p_buy_1_detail))
        assertEquals("n4602.0", text(R.id.text_p2p_buy_3_detail))
        assertEquals("n4499.0", text(R.id.text_p2p_sell_2_detail))
    }

    @Test
    fun `lines without an ad have no details`() {
        submit(P2pPrices(buy = ads(4600.0), sell = null))

        assertEquals("", text(R.id.text_p2p_buy_2_detail))
        assertEquals("", text(R.id.text_p2p_sell_1_detail))
    }

    @Test
    fun `price and details share one horizontal line that cannot wrap`() {
        val price = itemView.findViewById<View>(R.id.text_p2p_buy_1)
        val detail = itemView.findViewById<TextView>(R.id.text_p2p_buy_1_detail)

        assertEquals(price.parent, detail.parent)
        assertEquals(1, detail.maxLines)
    }
}
