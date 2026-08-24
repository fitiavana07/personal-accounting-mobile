package dev.fitiavana.accounting.features.balances

import dev.fitiavana.accounting.features.balances.GainLossCalculator
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
}
