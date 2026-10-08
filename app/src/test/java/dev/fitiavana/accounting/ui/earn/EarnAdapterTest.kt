package dev.fitiavana.accounting.ui.earn

import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.instruments.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class EarnAdapterTest {

    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Accounting)
    private val btc = Instrument(code = "BTC", note = "", type = "crypto", decimalPlaces = 8)
    private val usd = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)

    private fun state(vararg accounts: Account, balances: List<AccountBalance>): EarnState =
        EarnItemBuilder.build(accounts.toList(), balances, mapOf("BTC" to btc, "USD" to usd))

    private fun balance(id: String, base: Long, instrument: Long = 0L, intermediary: Long = 0L) =
        AccountBalance(
            accountId = id, balance = base, instrumentBalance = instrument,
            intermediaryBalance = intermediary, updatedAt = 0L, createdAt = 0L
        )

    private fun adapterWith(state: EarnState): EarnAdapter = EarnAdapter().also { it.submit(state) }

    private fun bindAt(adapter: EarnAdapter, position: Int): View {
        val holder: RecyclerView.ViewHolder =
            adapter.onCreateViewHolder(FrameLayout(context), adapter.getItemViewType(position))
        adapter.onBindViewHolder(holder, position)
        return holder.itemView
    }

    private fun text(view: View, id: Int) = view.findViewById<TextView>(id).text.toString()

    private val earn = Account(id = "earn", name = "Savings", type = "asset", aprPercent = 12.0)

    @Test
    fun `nothing to show gives no rows`() {
        assertEquals(0, adapterWith(EarnItemBuilder.build(emptyList(), emptyList(), emptyMap())).itemCount)
    }

    @Test
    fun `a totals row comes first, then one row per account`() {
        val adapter = adapterWith(state(earn, balances = listOf(balance("earn", 1_200_000L))))

        assertEquals(2, adapter.itemCount)
        assertEquals(EarnAdapter.VIEW_TYPE_TOTALS, adapter.getItemViewType(0))
        assertEquals(EarnAdapter.VIEW_TYPE_ITEM, adapter.getItemViewType(1))
    }

    @Test
    fun `totals lead with the monthly interest and add the daily and yearly ones below`() {
        val adapter = adapterWith(state(earn, balances = listOf(balance("earn", 1_200_000L))))

        val view = bindAt(adapter, 0)

        assertEquals("Ar 12,000", text(view, R.id.text_earn_totals_monthly))
        assertEquals("Ar 395 / day · Ar 144,000 / year", text(view, R.id.text_earn_totals_secondary))
    }

    @Test
    fun `an account row shows its name, APR, balance and base interest per period`() {
        val adapter = adapterWith(state(earn, balances = listOf(balance("earn", 1_200_000L))))

        val view = bindAt(adapter, 1)

        assertEquals("Savings", text(view, R.id.text_earn_name))
        assertEquals("12% APR", text(view, R.id.text_earn_apr))
        assertEquals("Balance: Ar 1,200,000", text(view, R.id.text_earn_balance))
        assertEquals("Ar 395", text(view, R.id.text_earn_daily))
        assertEquals("Ar 12,000", text(view, R.id.text_earn_monthly))
        assertEquals("Ar 144,000", text(view, R.id.text_earn_yearly))
    }

    @Test
    fun `the share bar shows the account share of the yearly interest`() {
        val other = Account(id = "other", name = "Other", type = "asset", aprPercent = 12.0)
        val adapter = adapterWith(
            state(earn, other, balances = listOf(balance("earn", 300_000L), balance("other", 100_000L)))
        )

        assertEquals(75, bindAt(adapter, 1).findViewById<ProgressBar>(R.id.progress_earn_share).progress)
        assertEquals(25, bindAt(adapter, 2).findViewById<ProgressBar>(R.id.progress_earn_share).progress)
    }

    @Test
    fun `an account without instruments has no native detail and is not expandable`() {
        val view = bindAt(adapterWith(state(earn, balances = listOf(balance("earn", 1_200_000L)))), 1)

        assertEquals(View.GONE, view.findViewById<View>(R.id.text_earn_native).visibility)
        assertFalse(view.isClickable)
    }

    @Test
    fun `instrument amounts are on the balance line and the interest detail is collapsed`() {
        val account = Account(id = "btc", name = "BTC Earn", type = "asset", instrumentCode = "BTC", aprPercent = 10.0)
        val adapter = adapterWith(state(account, balances = listOf(balance("btc", 5_000_000L, instrument = 200_000_000L))))

        val view = bindAt(adapter, 1)

        // The app shows instrument amounts with one decimal at least ("2.0 BTC"), see TransactionDisplay.
        assertEquals("Balance: Ar 5,000,000 · 2.0 BTC", text(view, R.id.text_earn_balance))
        assertEquals("Ar 500,000", text(view, R.id.text_earn_yearly))
        assertEquals(View.GONE, view.findViewById<View>(R.id.text_earn_native).visibility)
        assertTrue(view.isClickable)
    }

    @Test
    fun `tapping an account with instruments shows its native interest per period, tapping again hides it`() {
        val account = Account(id = "btc", name = "BTC Earn", type = "asset", instrumentCode = "BTC", aprPercent = 10.0)
        val adapter = adapterWith(state(account, balances = listOf(balance("btc", 5_000_000L, instrument = 200_000_000L))))
        val view = bindAt(adapter, 1)
        val native = view.findViewById<TextView>(R.id.text_earn_native)

        view.performClick()

        assertEquals(View.VISIBLE, native.visibility)
        assertEquals("Day: 0.00054795 BTC\nMonth: 0.01666667 BTC\nYear: 0.2 BTC", native.text.toString())

        view.performClick()

        assertEquals(View.GONE, native.visibility)
    }

    @Test
    fun `intermediary interest follows the instrument interest in the detail`() {
        val account = Account(
            id = "btc", name = "BTC Earn", type = "asset", instrumentCode = "BTC",
            intermediaryInstrumentCode = "USD", aprPercent = 10.0
        )
        val adapter = adapterWith(
            state(account, balances = listOf(balance("btc", 5_000_000L, instrument = 200_000_000L, intermediary = 120_000L)))
        )
        val view = bindAt(adapter, 1)

        view.performClick()

        assertEquals("Balance: Ar 5,000,000 · 2.0 BTC · 1,200.0 USD", text(view, R.id.text_earn_balance))
        assertTrue(text(view, R.id.text_earn_native).endsWith("Year: 0.2 BTC · 120.0 USD"))
    }

    @Test
    fun `an expanded account stays expanded when the data refreshes`() {
        val account = Account(id = "btc", name = "BTC Earn", type = "asset", instrumentCode = "BTC", aprPercent = 10.0)
        val st = state(account, balances = listOf(balance("btc", 5_000_000L, instrument = 200_000_000L)))
        val adapter = adapterWith(st)
        bindAt(adapter, 1).performClick()

        adapter.submit(st)

        assertEquals(View.VISIBLE, bindAt(adapter, 1).findViewById<View>(R.id.text_earn_native).visibility)
    }

    @Test
    fun `resubmitting replaces the rows`() {
        val adapter = adapterWith(state(earn, balances = listOf(balance("earn", 1_200_000L))))

        adapter.submit(EarnItemBuilder.build(emptyList(), emptyList(), emptyMap()))

        assertEquals(0, adapter.itemCount)
    }
}
