package dev.fitiavana.accounting.ui.alerts

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.alerts.AlertCheckManager
import dev.fitiavana.accounting.features.alerts.AlertRepository
import dev.fitiavana.accounting.features.alerts.AlertTypes
import dev.fitiavana.accounting.features.alerts.GainLossAlert
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AlertsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val btc = Instrument(code = "BTC", note = "", type = "cryptocurrency", decimalPlaces = 8, coingeckoId = "bitcoin")
    private val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)

    private val alertRepository = mock<AlertRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val balanceRepository = mock<BalanceRepository>()
    private val instrumentRepository = mock<InstrumentRepository>()
    private val alertCheckManager = mock<AlertCheckManager>()

    private val alerts = MutableLiveData<List<GainLossAlert>>(emptyList())
    private val accounts = MutableLiveData<List<Account>>(emptyList())
    private val balances = MutableLiveData<List<AccountBalance>>(emptyList())
    private val instruments = MutableLiveData<List<Instrument>>(emptyList())

    private val btcAccount = Account("acc", "My BTC", "asset", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT")
    private val cashAccount = Account("cash", "Cash", "asset")

    @Before
    fun setUp() {
        whenever(alertRepository.getAll()).thenReturn(alerts)
        whenever(accountRepository.getAll()).thenReturn(accounts)
        whenever(balanceRepository.getAll()).thenReturn(balances)
        whenever(instrumentRepository.getAll()).thenReturn(instruments)
    }

    private fun viewModel() = AlertsViewModel(
        alertRepository, accountRepository, balanceRepository, instrumentRepository, alertCheckManager
    ).also {
        it.items.observeForever {}
        it.accountChoices.observeForever {}
    }

    // --- list ---

    @Test
    fun `items pair each alert with its account name`() {
        val viewModel = viewModel()
        accounts.value = listOf(btcAccount, cashAccount)

        alerts.value = listOf(GainLossAlert("al1", "acc", AlertTypes.CROSSING_ABOVE, 10.0, 1L))

        val item = viewModel.items.value!!.single()
        assertEquals("My BTC", item.accountName)
        assertEquals("al1", item.alert.id)
    }

    @Test
    fun `account choices are only the accounts that have a gain loss on Home`() {
        val viewModel = viewModel()

        accounts.value = listOf(btcAccount, cashAccount)
        instruments.value = listOf(btc, usdt)
        balances.value = listOf(
            AccountBalance("acc", 300_000, 100_000_000, 6_000_000, 0L, 0L),
            AccountBalance("cash", 300_000, 0, 0, 0L, 0L)
        )

        assertEquals(listOf(AccountChoice("acc", "My BTC")), viewModel.accountChoices.value)
    }

    // --- validation and saving ---

    @Test
    fun `a missing account is rejected`() {
        val viewModel = viewModel()

        assertEquals(AlertSaveError.NO_ACCOUNT, viewModel.save(null, null, AlertTypes.CROSSING_ABOVE, "10"))
        verify(alertRepository, never()).create(any(), any(), any())
    }

    @Test
    fun `an empty, zero or unreadable threshold is rejected`() {
        val viewModel = viewModel()

        for (text in listOf("", " ", "0", "0.0", "-", "abc", "1e999")) {
            assertEquals(text, AlertSaveError.INVALID_THRESHOLD, viewModel.save(null, "acc", AlertTypes.CROSSING_ABOVE, text))
        }
        verify(alertRepository, never()).create(any(), any(), any())
    }

    @Test
    fun `parseThreshold accepts signed decimals`() {
        assertEquals(12.5, AlertsViewModel.parseThreshold("12.5")!!, 0.0)
        assertEquals(12.5, AlertsViewModel.parseThreshold("+12.5")!!, 0.0)
        assertEquals(-3.0, AlertsViewModel.parseThreshold(" -3 ")!!, 0.0)
        assertEquals(-3.5, AlertsViewModel.parseThreshold("-3,5")!!, 0.0)
        assertNull(AlertsViewModel.parseThreshold("0"))
    }

    @Test
    fun `saving a new alert creates it, arms the alarm and seeds its state`() {
        val viewModel = viewModel()

        assertNull(viewModel.saveSync(null, "acc", AlertTypes.CROSSING_BELOW, "-10"))

        val order = inOrder(alertRepository, alertCheckManager)
        order.verify(alertRepository).create("acc", AlertTypes.CROSSING_BELOW, -10.0)
        order.verify(alertCheckManager).ensureScheduled()
        order.verify(alertCheckManager).check()
    }

    @Test
    fun `saving an existing alert updates it and keeps its id`() {
        val viewModel = viewModel()
        val existing = GainLossAlert("al1", "acc", AlertTypes.CROSSING_ABOVE, 10.0, 5L)

        assertNull(viewModel.saveSync(existing, "acc", AlertTypes.CROSSING_BELOW, "-4.5"))

        verify(alertRepository).update(existing.copy(type = AlertTypes.CROSSING_BELOW, thresholdPercent = -4.5))
        verify(alertCheckManager).check()
    }

    @Test
    fun `deleting removes the alert`() {
        val viewModel = viewModel()

        viewModel.deleteSync("al1")

        verify(alertRepository).delete("al1")
    }

    @Test
    fun `ensureScheduled arms the alarm`() {
        viewModel().ensureScheduledSync()

        verify(alertCheckManager).ensureScheduled()
    }
}
