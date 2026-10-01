package dev.fitiavana.accounting.features.cexprices

import dev.fitiavana.accounting.network.cex.CexId
import dev.fitiavana.accounting.network.cex.CexPriceFetcher
import dev.fitiavana.accounting.network.cex.PairNotListedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class CexPriceRepositoryTest {

    private fun fetcher(block: (String, String) -> Double) = object : CexPriceFetcher {
        override fun fetchPrice(base: String, quote: String) = block(base, quote)
    }

    @Test
    fun `returns one entry per cex in enum order`() {
        val repo = CexPriceRepository(CexId.values().associateWith { fetcher { _, _ -> 1.0 } })

        assertEquals(CexId.values().toList(), repo.fetchAll("BTC", "USDT").map { it.cex })
    }

    @Test
    fun `successful fetch carries the price`() {
        val repo = CexPriceRepository(mapOf(CexId.BINANCE to fetcher { b, q -> if (b == "BTC" && q == "USDT") 65000.0 else 0.0 }))

        val result = repo.fetchAll("BTC", "USDT").single()

        assertEquals(65000.0, result.price!!, 0.0)
        assertNull(result.error)
    }

    @Test
    fun `failure of one cex does not affect the others`() {
        val repo = CexPriceRepository(
            mapOf(
                CexId.BINANCE to fetcher { _, _ -> 10.0 },
                CexId.BYBIT to fetcher { _, _ -> throw IOException("boom") },
                CexId.OKX to fetcher { _, _ -> throw PairNotListedException("none") }
            )
        )

        val byCex = repo.fetchAll("BTC", "USDT").associateBy { it.cex }

        assertEquals(10.0, byCex.getValue(CexId.BINANCE).price!!, 0.0)
        assertNull(byCex.getValue(CexId.BYBIT).price)
        assertNotNull(byCex.getValue(CexId.BYBIT).error)
        assertNull(byCex.getValue(CexId.OKX).price)
        assertEquals(PairNotListedException::class.java, byCex.getValue(CexId.OKX).error!!::class.java)
    }

    @Test
    fun `fetches run in parallel`() {
        val latch = java.util.concurrent.CountDownLatch(2)
        // Each fetcher waits for the other; only passes if they run concurrently.
        val waiting = fetcher { _, _ ->
            latch.countDown()
            if (!latch.await(2, java.util.concurrent.TimeUnit.SECONDS)) throw IOException("sequential")
            1.0
        }
        val repo = CexPriceRepository(mapOf(CexId.BINANCE to waiting, CexId.BYBIT to waiting))

        assertEquals(listOf(1.0, 1.0), repo.fetchAll("BTC", "USDT").map { it.price })
    }
}
