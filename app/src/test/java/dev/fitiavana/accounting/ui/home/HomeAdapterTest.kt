package dev.fitiavana.accounting.ui.home

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.instruments.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class HomeAdapterTest {

    private val btc = Instrument(code = "BTC", note = "", type = "cryptocurrency", decimalPlaces = 8, coingeckoId = "bitcoin")
    private val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)

    private fun item(id: String, gainLossAr: Double?, gainLoss: Double? = gainLossAr?.let { it / 4000 }) = HomeItem(
        accountId = id,
        accountName = "Account $id",
        instrument = btc,
        intermediaryInstrument = usdt,
        instrumentBalanceFormatted = "0.5 BTC",
        bookValue = 100.0,
        currentValue = gainLoss?.let { 100.0 + it },
        currentValueAr = gainLossAr?.let { 400_000 + it },
        gainLoss = gainLoss,
        gainLossPercent = gainLoss,
        bookValueAr = 400_000,
        gainLossAr = gainLossAr,
        bookRate = null,
        currentRate = null,
        rateFetchedAt = null
    )

    private fun inflate(adapter: HomeAdapter, position: Int): View {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(position))
        adapter.onBindViewHolder(holder, position)
        return holder.itemView
    }

    @Test
    fun `no items means no rows at all`() {
        val adapter = HomeAdapter { }
        adapter.submitList(emptyList())
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `a summary header sits above the account rows`() {
        val adapter = HomeAdapter { }
        adapter.submitList(listOf(item("1", 20_000.0), item("2", -5_000.0)))

        assertEquals(3, adapter.itemCount)
        val header = inflate(adapter, 0)
        assertEquals("+Ar 15,000", header.findViewById<TextView>(R.id.text_home_summary_gain_loss_ar).text.toString())
    }

    @Test
    fun `header is hidden of amounts when no item has a rate`() {
        val adapter = HomeAdapter { }
        adapter.submitList(listOf(item("1", null)))

        val header = inflate(adapter, 0)
        assertEquals(View.GONE, header.findViewById<View>(R.id.text_home_summary_gain_loss_ar).visibility)
    }

    @Test
    fun `account rows show name, holding and the Ar gain`() {
        val adapter = HomeAdapter { }
        adapter.submitList(listOf(item("1", 20_000.0)))

        val row = inflate(adapter, 1)
        assertEquals("Account 1", row.findViewById<TextView>(R.id.text_home_account_name).text.toString())
        assertEquals("0.5 BTC", row.findViewById<TextView>(R.id.text_home_account_balance).text.toString())
        assertEquals("+Ar 20,000", row.findViewById<TextView>(R.id.text_home_gain_loss_ar).text.toString())
    }

    @Test
    fun `clicking a row reports its item, not the header`() {
        var clicked: HomeItem? = null
        val adapter = HomeAdapter { clicked = it }
        adapter.submitList(listOf(item("1", 20_000.0), item("2", -5_000.0)))

        inflate(adapter, 2).performClick()

        assertEquals("2", clicked?.accountId)
    }

    @Test
    fun `clicking the header does nothing`() {
        var clicked: HomeItem? = null
        val adapter = HomeAdapter { clicked = it }
        adapter.submitList(listOf(item("1", 20_000.0)))

        val header = inflate(adapter, 0)
        header.performClick()

        assertNull(clicked)
        assertTrue(!header.isClickable)
    }
}
