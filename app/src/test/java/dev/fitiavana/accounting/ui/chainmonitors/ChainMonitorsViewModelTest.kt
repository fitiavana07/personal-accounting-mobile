package dev.fitiavana.accounting.ui.chainmonitors

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitor
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitorCheckManager
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitorRepository
import dev.fitiavana.accounting.network.evm.Erc20BalanceFetcher
import dev.fitiavana.accounting.network.evm.Erc20Codec
import dev.fitiavana.accounting.network.evm.RpcException
import dev.fitiavana.accounting.network.evm.TokenInfo
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChainMonitorsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val repository = mock<ChainMonitorRepository>()
    private val fetcher = mock<Erc20BalanceFetcher>()
    private val manager = mock<ChainMonitorCheckManager>()
    private val monitors = MutableLiveData<List<ChainMonitor>>(emptyList())

    private val vault = "0xB99B6dF96d4d5448cC0a5B3e0ef7896df9507Cf5"
    private val other = "0x" + "c".repeat(40)

    @Before
    fun setUp() {
        whenever(repository.getAll()).thenReturn(monitors)
        whenever(fetcher.fetchTokenInfo(Erc20Codec.BASE_USDC)).thenReturn(TokenInfo("USDC", 6))
    }

    private fun viewModel() = ChainMonitorsViewModel(repository, fetcher, manager)

    private fun form(
        label: String = "Acres 40 available",
        holder: String = vault,
        token: String = Erc20Codec.BASE_USDC,
        threshold: String = "0",
        interval: Int = 15
    ) = MonitorForm(label, holder, token, threshold, interval)

    private val existing = ChainMonitor("m1", "Old", vault, Erc20Codec.BASE_USDC, "USDC", 6, "0", 15, 5L)

    // --- validation ---

    @Test
    fun `a valid form has no error`() {
        assertNull(viewModel().validate(form()))
    }

    @Test
    fun `a blank label is rejected`() {
        assertEquals(MonitorSaveError.LABEL, viewModel().validate(form(label = "   ")))
    }

    @Test
    fun `malformed addresses are rejected`() {
        assertEquals(MonitorSaveError.HOLDER, viewModel().validate(form(holder = "0x123")))
        assertEquals(MonitorSaveError.TOKEN, viewModel().validate(form(token = "usdc")))
    }

    @Test
    fun `addresses are trimmed before they are checked`() {
        assertNull(viewModel().validate(form(holder = "  $vault ")))
    }

    @Test
    fun `thresholds must be a number of zero or more`() {
        assertEquals(MonitorSaveError.THRESHOLD, viewModel().validate(form(threshold = "")))
        assertEquals(MonitorSaveError.THRESHOLD, viewModel().validate(form(threshold = "abc")))
        assertEquals(MonitorSaveError.THRESHOLD, viewModel().validate(form(threshold = "-1")))
        assertNull(viewModel().validate(form(threshold = "0")))
        assertNull(viewModel().validate(form(threshold = "2,5")))
    }

    @Test
    fun `parseThreshold normalises the text`() {
        assertEquals("0", ChainMonitorsViewModel.parseThreshold("0"))
        assertEquals("0", ChainMonitorsViewModel.parseThreshold("0.000"))
        assertEquals("2.5", ChainMonitorsViewModel.parseThreshold(" 2,50 "))
        assertEquals("1000", ChainMonitorsViewModel.parseThreshold("1000"))
        assertEquals("0.000001", ChainMonitorsViewModel.parseThreshold("0.000001"))
        assertNull(ChainMonitorsViewModel.parseThreshold("1e3x"))
    }

    // --- saving ---

    @Test
    fun `a new monitor reads its token, is saved, scheduled and checked`() {
        val result = viewModel().saveSync(null, form(label = " Vault ", threshold = "0.50", interval = 30))

        assertNull(result)
        inOrder(repository, manager) {
            verify(repository).create("Vault", vault, Erc20Codec.BASE_USDC, "USDC", 6, "0.5", 30)
            verify(manager).refreshSchedule()
            verify(manager).check()
        }
    }

    @Test
    fun `an invalid form saves nothing`() {
        val result = viewModel().saveSync(null, form(holder = "nope"))

        assertEquals(MonitorSaveError.HOLDER, result)
        verify(repository, never()).create(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(),
            org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `editing keeps the id and does not read the token again when it is unchanged`() {
        val result = viewModel().saveSync(existing, form(label = "New", holder = other, threshold = "5", interval = 30))

        assertNull(result)
        verify(fetcher, never()).fetchTokenInfo(org.mockito.kotlin.any())
        verify(repository).update(
            existing.copy(label = "New", holderAddress = other, threshold = "5", intervalMinutes = 30)
        )
        verify(manager).refreshSchedule()
        verify(manager).check()
    }

    @Test
    fun `editing to another token reads that token`() {
        val dai = "0x" + "d".repeat(40)
        whenever(fetcher.fetchTokenInfo(dai)).thenReturn(TokenInfo("DAI", 18))

        val result = viewModel().saveSync(existing, form(token = dai))

        assertNull(result)
        verify(repository).update(existing.copy(label = "Acres 40 available", tokenAddress = dai, tokenSymbol = "DAI", tokenDecimals = 18))
    }

    @Test
    fun `an address that is not a token is reported and nothing is saved`() {
        whenever(fetcher.fetchTokenInfo(Erc20Codec.BASE_USDC)).thenThrow(RpcException.Malformed("no contract", null))

        assertEquals(MonitorSaveError.NOT_A_TOKEN, viewModel().saveSync(null, form()))
        verify(manager, never()).refreshSchedule()
    }

    @Test
    fun `a reverted call also means it is not a token`() {
        whenever(fetcher.fetchTokenInfo(Erc20Codec.BASE_USDC)).thenThrow(RpcException.Rpc("execution reverted"))

        assertEquals(MonitorSaveError.NOT_A_TOKEN, viewModel().saveSync(null, form()))
    }

    @Test
    fun `no connection while reading the token is reported`() {
        whenever(fetcher.fetchTokenInfo(Erc20Codec.BASE_USDC)).thenThrow(RpcException.NoNetwork(null))

        assertEquals(MonitorSaveError.NO_NETWORK, viewModel().saveSync(null, form()))
    }

    @Test
    fun `any other failure while reading the token is reported`() {
        whenever(fetcher.fetchTokenInfo(Erc20Codec.BASE_USDC)).thenThrow(IOException("boom"))

        assertEquals(MonitorSaveError.FAILED, viewModel().saveSync(null, form()))
    }

    // --- deleting and scheduling ---

    @Test
    fun `deleting removes the monitor then refreshes the schedule`() {
        viewModel().deleteSync("m1")

        inOrder(repository, manager) {
            verify(repository).delete("m1")
            verify(manager).refreshSchedule()
        }
    }

    @Test
    fun `ensureScheduled refreshes the schedule and tells whether monitors exist`() {
        whenever(repository.getAllSync()).thenReturn(listOf(existing))

        assertTrue(viewModel().ensureScheduledSync())
        verify(manager).refreshSchedule()

        whenever(repository.getAllSync()).thenReturn(emptyList())
        assertFalse(viewModel().ensureScheduledSync())
    }

    @Test
    fun `the monitors come from the repository`() {
        val vm = viewModel().also { it.items.observeForever {} }

        monitors.value = listOf(existing)

        assertEquals(listOf(existing), vm.items.value)
    }
}
