package dev.fitiavana.accounting

import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.ui.home.GainLossCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GainLossCalculatorTest {

    // --- computeCurrentValue ---

    @Test
    fun `current value scales by instrument decimal places`() {
        // 2 NVDA shares (decimalPlaces=0), rate 132.45 USDT per share
        val value = GainLossCalculator.computeCurrentValue(2, 0, 132.45)
        assertEquals(264.90, value, 0.0001)
    }

    @Test
    fun `current value with fractional instrument amount`() {
        // 0.5 BTC (decimalPlaces=8 -> instrumentBalance in satoshis), rate 65000 USDT
        val halfBtcInSatoshis = 50_000_000L
        val value = GainLossCalculator.computeCurrentValue(halfBtcInSatoshis, 8, 65000.0)
        assertEquals(32500.0, value, 0.0001)
    }

    // --- computeGainLoss ---

    @Test
    fun `gain loss is current minus book value`() {
        assertEquals(650.0, GainLossCalculator.computeGainLoss(4850.0, 4200.0), 0.0001)
        assertEquals(-350.0, GainLossCalculator.computeGainLoss(3850.0, 4200.0), 0.0001)
    }

    // --- computeGainLossPercent ---

    @Test
    fun `gain loss percent computed from book value`() {
        val percent = GainLossCalculator.computeGainLossPercent(650.0, 4200.0)
        assertEquals(15.476190, percent!!, 0.0001)
    }

    @Test
    fun `gain loss percent returns null when book value is zero`() {
        assertNull(GainLossCalculator.computeGainLossPercent(100.0, 0.0))
    }

    // --- formatSignedAmount ---

    @Test
    fun `format signed amount adds plus sign for gain`() {
        val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("+650.0 USDT", GainLossCalculator.formatSignedAmount(650.0, usdt))
    }

    @Test
    fun `format signed amount adds minus sign for loss`() {
        val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("-350.0 USDT", GainLossCalculator.formatSignedAmount(-350.0, usdt))
    }

    @Test
    fun `format signed amount for zero uses plus sign`() {
        val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("+0.0 USDT", GainLossCalculator.formatSignedAmount(0.0, usdt))
    }

    // --- formatSignedAmountAr ---

    @Test
    fun `format signed amount Ar adds plus sign for gain`() {
        assertEquals("+Ar 25,000", GainLossCalculator.formatSignedAmountAr(25000.0))
    }

    @Test
    fun `format signed amount Ar adds minus sign for loss`() {
        assertEquals("-Ar 25,000", GainLossCalculator.formatSignedAmountAr(-25000.0))
    }

    // --- formatSignedPercent ---

    @Test
    fun `format signed percent for gain`() {
        assertEquals("+15.5%", GainLossCalculator.formatSignedPercent(15.476190))
    }

    @Test
    fun `format signed percent for loss`() {
        assertEquals("-8.3%", GainLossCalculator.formatSignedPercent(-8.333))
    }
}
