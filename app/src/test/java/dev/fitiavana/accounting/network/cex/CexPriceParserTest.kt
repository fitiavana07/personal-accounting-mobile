package dev.fitiavana.accounting.network.cex

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class CexPriceParserTest {

    private fun assertNotListed(block: () -> Unit) {
        try {
            block()
            fail("Expected PairNotListedException")
        } catch (e: PairNotListedException) {
            // expected
        }
    }

    @Test
    fun `binance and mexc style top-level price`() {
        val json = """{"symbol":"BTCUSDT","price":"65000.50000000"}"""
        assertEquals(65000.5, CexPriceParser.parseTopLevelPrice(json), 0.0001)
    }

    @Test
    fun `top-level price missing is not listed`() {
        assertNotListed { CexPriceParser.parseTopLevelPrice("""{"code":-1121,"msg":"Invalid symbol."}""") }
    }

    @Test
    fun `bybit last price`() {
        val json = """{"retCode":0,"result":{"category":"spot","list":[{"symbol":"BTCUSDT","lastPrice":"65001.2"}]}}"""
        assertEquals(65001.2, CexPriceParser.parseBybit(json), 0.0001)
    }

    @Test
    fun `bybit empty list is not listed`() {
        assertNotListed { CexPriceParser.parseBybit("""{"retCode":0,"result":{"category":"spot","list":[]}}""") }
    }

    @Test
    fun `bitget last price`() {
        val json = """{"code":"00000","data":[{"symbol":"BTCUSDT","lastPr":"65002.3"}]}"""
        assertEquals(65002.3, CexPriceParser.parseBitget(json), 0.0001)
    }

    @Test
    fun `bitget empty data is not listed`() {
        assertNotListed { CexPriceParser.parseBitget("""{"code":"00000","data":[]}""") }
    }

    @Test
    fun `okx last price`() {
        val json = """{"code":"0","data":[{"instId":"BTC-USDT","last":"65003.4"}]}"""
        assertEquals(65003.4, CexPriceParser.parseOkx(json), 0.0001)
    }

    @Test
    fun `okx empty data is not listed`() {
        assertNotListed { CexPriceParser.parseOkx("""{"code":"51001","msg":"Instrument ID doesn't exist","data":[]}""") }
    }

    @Test
    fun `kucoin level1 price`() {
        val json = """{"code":"200000","data":{"time":1,"price":"65004.5","size":"0.1"}}"""
        assertEquals(65004.5, CexPriceParser.parseKucoin(json), 0.0001)
    }

    @Test
    fun `kucoin null data is not listed`() {
        assertNotListed { CexPriceParser.parseKucoin("""{"code":"200000","data":null}""") }
    }

    @Test
    fun `kraken last trade price from dynamic result key`() {
        val json = """{"error":[],"result":{"XBTUSDT":{"c":["65005.6","0.01"]}}}"""
        assertEquals(65005.6, CexPriceParser.parseKraken(json), 0.0001)
    }

    @Test
    fun `kraken error is not listed`() {
        assertNotListed { CexPriceParser.parseKraken("""{"error":["EQuery:Unknown asset pair"]}""") }
    }

    @Test
    fun `non-numeric price is not listed`() {
        assertNotListed { CexPriceParser.parseTopLevelPrice("""{"price":"abc"}""") }
    }
}
