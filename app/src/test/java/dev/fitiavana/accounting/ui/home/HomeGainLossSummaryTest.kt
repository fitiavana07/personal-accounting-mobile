package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.instruments.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeGainLossSummaryTest {

    private val btc = Instrument(code = "BTC", note = "", type = "cryptocurrency", decimalPlaces = 8, coingeckoId = "bitcoin")
    private val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)

    private fun item(bookValueAr: Long, gainLossAr: Double?) = HomeItem(
        accountId = "a",
        accountName = "A",
        instrument = btc,
        intermediaryInstrument = usdt,
        instrumentBalanceFormatted = "",
        bookValue = 0.0,
        currentValue = null,
        currentValueAr = gainLossAr?.let { bookValueAr + it },
        gainLoss = null,
        gainLossPercent = null,
        bookValueAr = bookValueAr,
        gainLossAr = gainLossAr,
        bookRate = null,
        currentRate = null,
        rateFetchedAt = null
    )

    @Test
    fun `no items yields no summary`() {
        assertNull(HomeGainLossSummary.of(emptyList()))
    }

    @Test
    fun `items without a rate yield no summary`() {
        assertNull(HomeGainLossSummary.of(listOf(item(100_000, null))))
    }

    @Test
    fun `sums gain loss in Ar across items`() {
        val summary = HomeGainLossSummary.of(
            listOf(item(100_000, 20_000.0), item(50_000, -5_000.0))
        )!!
        assertEquals(15_000.0, summary.gainLossAr, 0.0001)
        assertEquals(150_000L, summary.bookValueAr)
    }

    @Test
    fun `percent is relative to the total book value`() {
        val summary = HomeGainLossSummary.of(
            listOf(item(100_000, 20_000.0), item(100_000, -10_000.0))
        )!!
        assertEquals(5.0, summary.gainLossPercent!!, 0.0001)
    }

    @Test
    fun `items without a rate are left out of the totals`() {
        val summary = HomeGainLossSummary.of(
            listOf(item(100_000, 10_000.0), item(900_000, null))
        )!!
        assertEquals(10_000.0, summary.gainLossAr, 0.0001)
        assertEquals(100_000L, summary.bookValueAr)
        assertEquals(10.0, summary.gainLossPercent!!, 0.0001)
    }
}
