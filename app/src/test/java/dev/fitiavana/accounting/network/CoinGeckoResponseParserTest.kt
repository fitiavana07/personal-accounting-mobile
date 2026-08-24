package dev.fitiavana.accounting.network

import dev.fitiavana.accounting.network.CoinGeckoResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class CoinGeckoResponseParserTest {

    @Test
    fun `single ticker parsed with target exchange and price`() {
        val json = """{"tickers":[{"target":"USDT","last":65000.5,"market":{"identifier":"binance"}}]}"""
        val result = CoinGeckoResponseParser.parseTickers(json)
        assertEquals(1, result.size)
        assertEquals("USDT", result[0].target)
        assertEquals("binance", result[0].exchangeIdentifier)
        assertEquals(65000.5, result[0].last, 0.0001)
    }

    @Test
    fun `multiple tickers for different targets`() {
        val json = """{"tickers":[
            {"target":"USDT","last":65000.5,"market":{"identifier":"binance"}},
            {"target":"USD","last":64900.0,"market":{"identifier":"binance"}}
        ]}"""
        val result = CoinGeckoResponseParser.parseTickers(json)
        assertEquals(2, result.size)
        assertEquals("USDT", result[0].target)
        assertEquals("USD", result[1].target)
    }

    @Test
    fun `ticker missing target is skipped`() {
        val json = """{"tickers":[{"last":65000.5,"market":{"identifier":"binance"}}]}"""
        val result = CoinGeckoResponseParser.parseTickers(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ticker missing market identifier defaults to empty string`() {
        val json = """{"tickers":[{"target":"USDT","last":65000.5}]}"""
        val result = CoinGeckoResponseParser.parseTickers(json)
        assertEquals("", result[0].exchangeIdentifier)
    }

    @Test
    fun `no tickers array returns empty list`() {
        val result = CoinGeckoResponseParser.parseTickers("{}")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty tickers array returns empty list`() {
        val result = CoinGeckoResponseParser.parseTickers("""{"tickers":[]}""")
        assertTrue(result.isEmpty())
    }
}
