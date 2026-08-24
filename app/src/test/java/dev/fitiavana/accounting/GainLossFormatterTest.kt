package dev.fitiavana.accounting

import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.ui.home.GainLossFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class GainLossFormatterTest {

    // --- formatSignedAmount ---

    @Test
    fun `format signed amount adds plus sign for gain`() {
        val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("+650.0 USDT", GainLossFormatter.formatSignedAmount(650.0, usdt))
    }

    @Test
    fun `format signed amount adds minus sign for loss`() {
        val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("-350.0 USDT", GainLossFormatter.formatSignedAmount(-350.0, usdt))
    }

    @Test
    fun `format signed amount for zero uses plus sign`() {
        val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("+0.0 USDT", GainLossFormatter.formatSignedAmount(0.0, usdt))
    }

    // --- formatSignedAmountAr ---

    @Test
    fun `format signed amount Ar adds plus sign for gain`() {
        assertEquals("+Ar 25,000", GainLossFormatter.formatSignedAmountAr(25000.0))
    }

    @Test
    fun `format signed amount Ar adds minus sign for loss`() {
        assertEquals("-Ar 25,000", GainLossFormatter.formatSignedAmountAr(-25000.0))
    }

    // --- formatSignedPercent ---

    @Test
    fun `format signed percent for gain`() {
        assertEquals("+15.5%", GainLossFormatter.formatSignedPercent(15.476190))
    }

    @Test
    fun `format signed percent for loss`() {
        assertEquals("-8.3%", GainLossFormatter.formatSignedPercent(-8.333))
    }
}
