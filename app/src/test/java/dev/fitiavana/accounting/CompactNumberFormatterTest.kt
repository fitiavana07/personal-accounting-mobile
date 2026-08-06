package dev.fitiavana.accounting

import dev.fitiavana.accounting.ui.home.CompactNumberFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class CompactNumberFormatterTest {

    @Test
    fun `amounts below 1000 render without a suffix`() {
        assertEquals("500", CompactNumberFormatter.format(500))
    }

    @Test
    fun `thousands render with two integer digits and one decimal`() {
        assertEquals("10.0K", CompactNumberFormatter.format(10_000))
    }

    @Test
    fun `millions with a single integer digit render with two decimals`() {
        assertEquals("1.24M", CompactNumberFormatter.format(1_240_000))
    }

    @Test
    fun `millions with two integer digits render with one decimal`() {
        assertEquals("30.1M", CompactNumberFormatter.format(30_100_000))
    }

    @Test
    fun `billions use the B suffix`() {
        assertEquals("2.50B", CompactNumberFormatter.format(2_500_000_000))
    }

    @Test
    fun `negative amounts keep their sign`() {
        assertEquals("-10.0K", CompactNumberFormatter.format(-10_000))
    }

    @Test
    fun `rounding up to the next unit boundary bumps the suffix`() {
        assertEquals("1.00M", CompactNumberFormatter.format(999_950))
    }
}
