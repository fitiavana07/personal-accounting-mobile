package dev.fitiavana.accounting.data.network

import dev.fitiavana.accounting.data.network.YahooFinanceResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class YahooFinanceResponseParserTest {

    @Test
    fun `US stock quote parsed with symbol currency and price`() {
        val json = """{"chart":{"result":[{"meta":{"currency":"USD","symbol":"NVDA","regularMarketPrice":123.45}}],"error":null}}"""
        val result = YahooFinanceResponseParser.parseQuote(json)
        assertEquals("NVDA", result?.symbol)
        assertEquals("USD", result?.currency)
        assertEquals(123.45, result?.price!!, 0.0001)
    }

    @Test
    fun `Hong Kong stock quote parsed with HKD currency`() {
        val json = """{"chart":{"result":[{"meta":{"currency":"HKD","symbol":"1810.HK","regularMarketPrice":15.32}}],"error":null}}"""
        val result = YahooFinanceResponseParser.parseQuote(json)
        assertEquals("1810.HK", result?.symbol)
        assertEquals("HKD", result?.currency)
        assertEquals(15.32, result?.price!!, 0.0001)
    }

    @Test
    fun `missing currency returns null`() {
        val json = """{"chart":{"result":[{"meta":{"symbol":"NVDA","regularMarketPrice":123.45}}],"error":null}}"""
        assertNull(YahooFinanceResponseParser.parseQuote(json))
    }

    @Test
    fun `missing price returns null`() {
        val json = """{"chart":{"result":[{"meta":{"currency":"USD","symbol":"NVDA"}}],"error":null}}"""
        assertNull(YahooFinanceResponseParser.parseQuote(json))
    }

    @Test
    fun `empty result array returns null`() {
        val json = """{"chart":{"result":[],"error":null}}"""
        assertNull(YahooFinanceResponseParser.parseQuote(json))
    }

    @Test
    fun `missing chart returns null`() {
        assertNull(YahooFinanceResponseParser.parseQuote("{}"))
    }
}
