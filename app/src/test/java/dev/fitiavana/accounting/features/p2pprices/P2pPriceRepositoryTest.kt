package dev.fitiavana.accounting.features.p2pprices

import dev.fitiavana.accounting.network.p2p.P2pAd
import dev.fitiavana.accounting.network.p2p.P2pPaymentMethod
import dev.fitiavana.accounting.network.p2p.P2pPriceFetcher
import dev.fitiavana.accounting.network.p2p.P2pSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class P2pPriceRepositoryTest {

    private class FakeStore : P2pFilterStore {
        var selected: String? = null
        var methods: List<P2pPaymentMethod>? = null
        override fun loadSelected() = selected
        override fun saveSelected(identifier: String?) {
            selected = identifier
        }
        override fun loadMethods() = methods
        override fun saveMethods(methods: List<P2pPaymentMethod>) {
            this.methods = methods
        }
    }

    private class FakeFetcher(
        val prices: (P2pSide, String?) -> List<P2pAd> = { _, _ -> emptyList() },
        var methods: () -> List<P2pPaymentMethod> = { emptyList() }
    ) : P2pPriceFetcher {
        val priceCalls = java.util.Collections.synchronizedList(ArrayList<Pair<P2pSide, String?>>())
        var methodCalls = 0
        override fun fetchTopPrices(side: P2pSide, payType: String?): List<P2pAd> {
            priceCalls.add(side to payType)
            return prices(side, payType)
        }
        override fun fetchPaymentMethods(): List<P2pPaymentMethod> {
            methodCalls++
            return methods()
        }
    }

    private val store = FakeStore()
    private val mvola = P2pPaymentMethod("Mvola", "Mvola")
    private val orange = P2pPaymentMethod("OrangeMoney", "Orange Money - OM")

    private fun ads(vararg prices: Double) = prices.map { P2pAd(it) }

    @Test
    fun `buy and sell prices come from their own side`() {
        val repo = P2pPriceRepository(
            FakeFetcher({ side, _ -> if (side == P2pSide.BUY) ads(4600.0, 4601.0, 4602.0) else ads(4500.0, 4499.0) }),
            store
        )

        val result = repo.fetch()

        assertEquals(ads(4600.0, 4601.0, 4602.0), result.buy)
        assertEquals(ads(4500.0, 4499.0), result.sell)
    }

    @Test
    fun `a failing side is null and does not affect the other`() {
        val repo = P2pPriceRepository(
            FakeFetcher({ side, _ -> if (side == P2pSide.BUY) throw IOException("boom") else ads(4500.0) }),
            store
        )

        val result = repo.fetch()

        assertNull(result.buy)
        assertEquals(ads(4500.0), result.sell)
    }

    @Test
    fun `no selection fetches unfiltered for both sides`() {
        val fetcher = FakeFetcher({ _, _ -> ads(1.0) })

        val result = P2pPriceRepository(fetcher, store).fetch()

        assertEquals(setOf<Pair<P2pSide, String?>>(P2pSide.BUY to null, P2pSide.SELL to null), fetcher.priceCalls.toSet())
        assertNull(result.filter)
    }

    @Test
    fun `selected method filters both sides and is reported with its cached name`() {
        store.selected = "OrangeMoney"
        store.methods = listOf(mvola, orange)
        val fetcher = FakeFetcher({ _, _ -> ads(1.0) })

        val result = P2pPriceRepository(fetcher, store).fetch()

        assertEquals(setOf<Pair<P2pSide, String?>>(P2pSide.BUY to "OrangeMoney", P2pSide.SELL to "OrangeMoney"), fetcher.priceCalls.toSet())
        assertEquals(orange, result.filter)
        assertEquals(0, fetcher.methodCalls)
    }

    @Test
    fun `selected method without a cached name is reported by identifier`() {
        store.selected = "Mvola"

        val result = P2pPriceRepository(FakeFetcher({ _, _ -> ads(1.0) }), store).fetch()

        assertEquals(P2pPaymentMethod("Mvola", "Mvola"), result.filter)
    }

    @Test
    fun `empty result with a filter refreshes methods and drops a selection that no longer exists`() {
        store.selected = "OldMethod"
        val fetcher = FakeFetcher(
            { _, payType -> if (payType == null) ads(4600.0) else emptyList() },
            methods = { listOf(mvola, orange) }
        )

        val result = P2pPriceRepository(fetcher, store).fetch()

        assertEquals(1, fetcher.methodCalls)
        assertEquals(listOf(mvola, orange), store.methods)
        assertNull(store.selected)
        assertNull(result.filter)
        assertEquals(ads(4600.0), result.buy)
        assertEquals(ads(4600.0), result.sell)
    }

    @Test
    fun `empty result with a still valid filter keeps the selection`() {
        store.selected = "Mvola"
        val fetcher = FakeFetcher({ _, _ -> emptyList() }, methods = { listOf(mvola, orange) })

        val result = P2pPriceRepository(fetcher, store).fetch()

        assertEquals("Mvola", store.selected)
        assertEquals(mvola, result.filter)
        assertEquals(emptyList<P2pAd>(), result.buy)
    }

    @Test
    fun `methods refresh failure keeps the selection`() {
        store.selected = "Mvola"
        val fetcher = FakeFetcher({ _, _ -> emptyList() }, methods = { throw IOException("offline") })

        P2pPriceRepository(fetcher, store).fetch()

        assertEquals("Mvola", store.selected)
    }

    @Test
    fun `one side empty does not trigger a methods refresh`() {
        store.selected = "Mvola"
        val fetcher = FakeFetcher({ side, _ -> if (side == P2pSide.BUY) ads(1.0) else emptyList() })

        P2pPriceRepository(fetcher, store).fetch()

        assertEquals(0, fetcher.methodCalls)
    }

    @Test
    fun `getPaymentMethods uses the cache without fetching`() {
        store.methods = listOf(mvola)
        val fetcher = FakeFetcher()

        assertEquals(listOf(mvola), P2pPriceRepository(fetcher, store).getPaymentMethods())
        assertEquals(0, fetcher.methodCalls)
    }

    @Test
    fun `getPaymentMethods fetches and caches when nothing is cached`() {
        val fetcher = FakeFetcher(methods = { listOf(mvola, orange) })

        assertEquals(listOf(mvola, orange), P2pPriceRepository(fetcher, store).getPaymentMethods())
        assertEquals(listOf(mvola, orange), store.methods)
    }

    @Test
    fun `getPaymentMethods is empty when offline with no cache`() {
        val fetcher = FakeFetcher(methods = { throw IOException("offline") })

        assertEquals(emptyList<P2pPaymentMethod>(), P2pPriceRepository(fetcher, store).getPaymentMethods())
    }

    @Test
    fun `selectMethod persists the selection and null clears it`() {
        val repo = P2pPriceRepository(FakeFetcher(), store)

        repo.selectMethod("Mvola")
        assertEquals("Mvola", repo.getSelectedMethod())

        repo.selectMethod(null)
        assertNull(repo.getSelectedMethod())
    }
}
