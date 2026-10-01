package dev.fitiavana.accounting.network.cex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CexEndpointsTest {

    @Test
    fun `every cex has an endpoint`() {
        assertEquals(CexId.values().toSet(), CexEndpoints.all.keys)
    }

    @Test
    fun `concatenated symbol style`() {
        assertEquals(
            "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT",
            CexEndpoints.all.getValue(CexId.BINANCE).url("BTC", "USDT")
        )
        assertEquals(
            "https://api.mexc.com/api/v3/ticker/price?symbol=ETHUSDC",
            CexEndpoints.all.getValue(CexId.MEXC).url("ETH", "USDC")
        )
        assertTrue(CexEndpoints.all.getValue(CexId.BYBIT).url("BTC", "USDT").endsWith("category=spot&symbol=BTCUSDT"))
        assertTrue(CexEndpoints.all.getValue(CexId.BITGET).url("BTC", "USDT").endsWith("symbol=BTCUSDT"))
    }

    @Test
    fun `dash separated symbol style`() {
        assertTrue(CexEndpoints.all.getValue(CexId.OKX).url("BTC", "USDT").endsWith("instId=BTC-USDT"))
        assertTrue(CexEndpoints.all.getValue(CexId.KUCOIN).url("BTC", "USDT").endsWith("symbol=BTC-USDT"))
    }

    @Test
    fun `kraken maps BTC to XBT`() {
        assertTrue(CexEndpoints.all.getValue(CexId.KRAKEN).url("BTC", "USDT").endsWith("pair=XBTUSDT"))
        assertTrue(CexEndpoints.all.getValue(CexId.KRAKEN).url("ETH", "BTC").endsWith("pair=ETHXBT"))
    }

    @Test
    fun `codes are uppercased`() {
        assertTrue(CexEndpoints.all.getValue(CexId.BINANCE).url("btc", "usdt").endsWith("BTCUSDT"))
    }
}
