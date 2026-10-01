package dev.fitiavana.accounting.ui.cexprices

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.cexprices.CexPrice
import dev.fitiavana.accounting.features.cexprices.CexPriceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.network.cex.CexId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor

class CexPricesViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var instrumentRepository: InstrumentRepository
    private lateinit var cexPriceRepository: CexPriceRepository
    private lateinit var cryptos: MutableLiveData<List<Instrument>>
    private lateinit var viewModel: CexPricesViewModel

    private val result = listOf(CexPrice(CexId.BINANCE, 65000.0, null))

    @Before
    fun setUp() {
        instrumentRepository = mock()
        cexPriceRepository = mock()
        cryptos = MutableLiveData()
        whenever(instrumentRepository.getCryptocurrencies()).thenReturn(cryptos)
        whenever(cexPriceRepository.fetchAll(any(), any())).thenReturn(result)
        viewModel = CexPricesViewModel(instrumentRepository, cexPriceRepository, Executor { it.run() })
    }

    @Test
    fun `exposes cryptocurrency instruments from repository`() {
        val list = listOf(Instrument("BTC", "", Instrument.TYPE_CRYPTOCURRENCY))
        cryptos.value = list

        assertEquals(list, viewModel.cryptoInstruments.value)
    }

    @Test
    fun `no fetch until both base and quote are chosen`() {
        viewModel.selectBase("BTC")

        verify(cexPriceRepository, never()).fetchAll(any(), any())
        assertNull(viewModel.prices.value)
    }

    @Test
    fun `fetches once both are chosen`() {
        viewModel.selectBase("BTC")
        viewModel.selectQuote("USDT")

        verify(cexPriceRepository).fetchAll("BTC", "USDT")
        assertEquals(result, viewModel.prices.value)
        assertFalse(viewModel.loading.value!!)
    }

    @Test
    fun `same base and quote does not fetch and clears prices`() {
        viewModel.selectBase("BTC")
        viewModel.selectQuote("USDT")

        viewModel.selectQuote("BTC")

        verify(cexPriceRepository, times(1)).fetchAll(any(), any())
        assertNull(viewModel.prices.value)
    }

    @Test
    fun `changing selection fetches again`() {
        viewModel.selectBase("BTC")
        viewModel.selectQuote("USDT")
        viewModel.selectQuote("USDC")

        verify(cexPriceRepository).fetchAll("BTC", "USDC")
    }

    @Test
    fun `selecting the same value again does not refetch`() {
        viewModel.selectBase("BTC")
        viewModel.selectQuote("USDT")
        viewModel.selectQuote("USDT")

        verify(cexPriceRepository, times(1)).fetchAll(any(), any())
    }

    @Test
    fun `refresh refetches current pair`() {
        viewModel.selectBase("BTC")
        viewModel.selectQuote("USDT")

        viewModel.refresh()

        verify(cexPriceRepository, times(2)).fetchAll("BTC", "USDT")
    }

    @Test
    fun `refresh without a pair does nothing`() {
        viewModel.refresh()

        verify(cexPriceRepository, never()).fetchAll(any(), any())
    }

    @Test
    fun `stale result is ignored when selection changed while fetching`() {
        val pending = mutableListOf<Runnable>()
        val vm = CexPricesViewModel(instrumentRepository, cexPriceRepository, Executor { pending.add(it) })
        vm.selectBase("BTC")
        vm.selectQuote("USDT")
        vm.selectQuote("BTC") // invalidates the in-flight request

        pending.forEach { it.run() }

        assertNull(vm.prices.value)
        assertFalse(vm.loading.value!!)
    }
}
