package dev.fitiavana.accounting.features.alerts

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.exchangerates.RateRefresher
import dev.fitiavana.accounting.features.exchangerates.RefreshResult
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AlertCheckManagerTest {

    private val btc = Instrument(code = "BTC", note = "", type = "cryptocurrency", decimalPlaces = 8, coingeckoId = "bitcoin")
    private val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)

    private val alertRepository = mock<AlertRepository>()
    private val balanceRepository = mock<BalanceRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val instrumentRepository = mock<InstrumentRepository>()
    private val exchangeRateRepository = mock<ExchangeRateRepository>()
    private val rateRefresher = mock<RateRefresher>()

    private class FakeNotifier : AlertNotifier {
        val fired = mutableListOf<Pair<FiredAlert, String>>()
        override fun notifyFired(fired: FiredAlert, accountName: String) {
            this.fired += fired to accountName
        }
    }

    private class FakeScheduler : AlertScheduler {
        val scheduled = mutableListOf<Long>()
        var cancelled = 0
        override fun scheduleAt(triggerAtMillis: Long) {
            scheduled += triggerAtMillis
        }

        override fun cancel() {
            cancelled++
        }
    }

    private val notifier = FakeNotifier()
    private val scheduler = FakeScheduler()
    private var now = 1_000_000L
    private lateinit var manager: AlertCheckManager

    private val alert = GainLossAlert("al1", "acc", AlertTypes.CROSSING_ABOVE, 10.0, 1L)

    @Before
    fun setUp() {
        manager = AlertCheckManager(
            alertRepository, balanceRepository, accountRepository, instrumentRepository,
            exchangeRateRepository, rateRefresher, notifier, scheduler
        ) { now }
        whenever(alertRepository.getAllSync()).thenReturn(listOf(alert))
        whenever(alertRepository.getStatesSync()).thenReturn(emptyList())
        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(Account("acc", "My BTC", "asset", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT"))
        )
        whenever(instrumentRepository.getAllSync()).thenReturn(listOf(btc, usdt))
        whenever(balanceRepository.getAllSync()).thenReturn(
            listOf(AccountBalance("acc", 300_000, 100_000_000, 6_000_000, 0L, 0L))
        )
        whenever(rateRefresher.refresh()).thenReturn(RefreshResult(emptyList(), emptyList(), null))
        rate(66_000.0) // +10%
    }

    private fun rate(value: Double) {
        whenever(exchangeRateRepository.getAllCachedSync()).thenReturn(
            listOf(ExchangeRateCache(ExchangeRateCache.pairKey("BTC", "USDT"), "BTC", "USDT", value, 1L))
        )
    }

    private fun armed() = whenever(alertRepository.getStatesSync())
        .thenReturn(listOf(AlertState("al1", true, alert.signature)))

    @Test
    fun `check notifies a crossed alert with its account name and saves the new state`() {
        armed()

        manager.check()

        assertEquals(1, notifier.fired.size)
        assertEquals("al1", notifier.fired[0].first.alert.id)
        assertEquals(10.0, notifier.fired[0].first.percent, 0.0001)
        assertEquals("My BTC", notifier.fired[0].second)
        val captor = argumentCaptor<List<AlertState>>()
        verify(alertRepository).saveStates(captor.capture())
        assertEquals(listOf(AlertState("al1", false, alert.signature)), captor.firstValue)
    }

    @Test
    fun `check seeds an alert without a state and does not notify`() {
        manager.check()

        assertTrue(notifier.fired.isEmpty())
        val captor = argumentCaptor<List<AlertState>>()
        verify(alertRepository).saveStates(captor.capture())
        assertEquals(listOf(AlertState("al1", false, alert.signature)), captor.firstValue)
    }

    @Test
    fun `check does nothing without alerts`() {
        whenever(alertRepository.getAllSync()).thenReturn(emptyList())

        manager.check()

        verify(alertRepository, never()).saveStates(any())
        assertTrue(notifier.fired.isEmpty())
    }

    @Test
    fun `an alarm refreshes the rates, checks, and arms the next alarm in an hour`() {
        armed()

        manager.onAlarm()

        verify(rateRefresher).refresh()
        assertEquals(1, notifier.fired.size)
        assertEquals(listOf(now + AlertCheckManager.INTERVAL_MILLIS), scheduler.scheduled)
    }

    @Test
    fun `an alarm still arms the next one when the refresh throws`() {
        whenever(rateRefresher.refresh()).thenThrow(RuntimeException("offline"))

        manager.onAlarm()

        assertEquals(listOf(now + AlertCheckManager.INTERVAL_MILLIS), scheduler.scheduled)
    }

    @Test
    fun `an alarm without alerts skips the network`() {
        whenever(alertRepository.getAllSync()).thenReturn(emptyList())

        manager.onAlarm()

        verify(rateRefresher, never()).refresh()
        assertEquals(listOf(now + AlertCheckManager.INTERVAL_MILLIS), scheduler.scheduled)
    }

    @Test
    fun `ensureScheduled arms an alarm in an hour`() {
        manager.ensureScheduled()

        assertEquals(listOf(now + AlertCheckManager.INTERVAL_MILLIS), scheduler.scheduled)
    }
}
