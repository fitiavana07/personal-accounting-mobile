package dev.fitiavana.accounting.ui.cexprices

import dev.fitiavana.accounting.features.cexprices.CexPrice
import dev.fitiavana.accounting.network.cex.CexId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CexPriceComparisonBuilderTest {

    private fun price(cex: CexId, value: Double?) =
        CexPrice(cex, value, if (value == null) IOException("x") else null)

    @Test
    fun `rows keep the input order`() {
        val result = CexPriceComparisonBuilder.build(
            listOf(price(CexId.BINANCE, 102.0), price(CexId.BYBIT, 100.0), price(CexId.OKX, 101.0))
        )

        assertEquals(listOf(CexId.BINANCE, CexId.BYBIT, CexId.OKX), result.rows.map { it.cex })
    }

    @Test
    fun `marks lowest and highest and computes difference from lowest`() {
        val result = CexPriceComparisonBuilder.build(
            listOf(price(CexId.BINANCE, 102.0), price(CexId.BYBIT, 100.0), price(CexId.OKX, 101.0))
        )
        val byCex = result.rows.associateBy { it.cex }

        assertTrue(byCex.getValue(CexId.BYBIT).isLowest)
        assertFalse(byCex.getValue(CexId.BYBIT).isHighest)
        assertTrue(byCex.getValue(CexId.BINANCE).isHighest)
        assertFalse(byCex.getValue(CexId.OKX).isLowest)
        assertFalse(byCex.getValue(CexId.OKX).isHighest)
        assertEquals(0.0, byCex.getValue(CexId.BYBIT).diffPercent!!, 0.0001)
        assertEquals(1.0, byCex.getValue(CexId.OKX).diffPercent!!, 0.0001)
        assertEquals(2.0, byCex.getValue(CexId.BINANCE).diffPercent!!, 0.0001)
    }

    @Test
    fun `spread is highest over lowest in percent`() {
        val result = CexPriceComparisonBuilder.build(listOf(price(CexId.BINANCE, 102.0), price(CexId.BYBIT, 100.0)))

        assertEquals(2.0, result.spreadPercent!!, 0.0001)
    }

    @Test
    fun `unavailable rows have no price diff or marks and are ignored in the comparison`() {
        val result = CexPriceComparisonBuilder.build(
            listOf(price(CexId.BINANCE, 100.0), price(CexId.BYBIT, null), price(CexId.OKX, 101.0))
        )
        val unavailable = result.rows.first { it.cex == CexId.BYBIT }

        assertNull(unavailable.price)
        assertNull(unavailable.diffPercent)
        assertFalse(unavailable.isLowest)
        assertFalse(unavailable.isHighest)
        assertEquals(1.0, result.spreadPercent!!, 0.0001)
    }

    @Test
    fun `a single priced exchange has no marks and no spread`() {
        val result = CexPriceComparisonBuilder.build(listOf(price(CexId.BINANCE, 100.0), price(CexId.BYBIT, null)))

        assertFalse(result.rows[0].isLowest)
        assertFalse(result.rows[0].isHighest)
        assertNull(result.spreadPercent)
    }

    @Test
    fun `identical prices have no marks and zero spread`() {
        val result = CexPriceComparisonBuilder.build(listOf(price(CexId.BINANCE, 100.0), price(CexId.BYBIT, 100.0)))

        assertTrue(result.rows.none { it.isLowest || it.isHighest })
        assertEquals(0.0, result.spreadPercent!!, 0.0001)
    }

    @Test
    fun `zero lowest price yields no percentages`() {
        val result = CexPriceComparisonBuilder.build(listOf(price(CexId.BINANCE, 0.0), price(CexId.BYBIT, 1.0)))

        assertNull(result.spreadPercent)
        assertNull(result.rows[1].diffPercent)
    }

    @Test
    fun `empty input gives empty comparison`() {
        val result = CexPriceComparisonBuilder.build(emptyList())

        assertTrue(result.rows.isEmpty())
        assertNull(result.spreadPercent)
    }
}
