package dev.fitiavana.accounting.ui.cexprices

import org.junit.Assert.assertEquals
import org.junit.Test

class CexPriceFormatterTest {

    @Test
    fun `large prices use two decimals and thousands separators`() {
        assertEquals("65,000.50", CexPriceFormatter.format(65000.5))
    }

    @Test
    fun `prices between one and a thousand use four decimals`() {
        assertEquals("1.5000", CexPriceFormatter.format(1.5))
        assertEquals("999.9999", CexPriceFormatter.format(999.9999))
    }

    @Test
    fun `sub-unit prices use eight decimals`() {
        assertEquals("0.00001234", CexPriceFormatter.format(0.00001234))
    }
}
