package dev.fitiavana.accounting.features.chainmonitors

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.network.evm.Erc20BalanceFetcher
import dev.fitiavana.accounting.network.evm.RpcException
import dev.fitiavana.accounting.network.evm.TokenInfo
import java.io.IOException
import java.math.BigDecimal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ChainMonitorCheckManagerTest {

    private val minute = 60_000L
    private val holderA = "0x" + "a".repeat(40)
    private val holderB = "0x" + "c".repeat(40)

    private class FakeFetcher : Erc20BalanceFetcher {
        val balances = mutableMapOf<String, Any>() // holder -> BigDecimal or Exception
        val fetched = mutableListOf<String>()
        override fun fetchBalance(holder: String, token: String, decimals: Int): BigDecimal {
            fetched += holder
            val answer = balances[holder] ?: error("no answer for $holder")
            if (answer is Exception) throw answer
            return answer as BigDecimal
        }

        override fun fetchTokenInfo(token: String) = TokenInfo("USDC", 6)
    }

    private class FakeNotifier : MonitorNotifier {
        val fired = mutableListOf<FiredMonitor>()
        var onNotify: () -> Unit = {}
        override fun notifyFired(fired: FiredMonitor) {
            onNotify()
            this.fired += fired
        }
    }

    private class FakeScheduler : MonitorScheduler {
        val scheduled = mutableListOf<Long>()
        var cancelled = 0
        override fun scheduleAt(triggerAtMillis: Long) {
            scheduled += triggerAtMillis
        }

        override fun cancel() {
            cancelled++
        }
    }

    private lateinit var db: AppDatabase
    private lateinit var repository: ChainMonitorRepository
    private val fetcher = FakeFetcher()
    private val notifier = FakeNotifier()
    private val scheduler = FakeScheduler()
    private var now = 10_000_000L
    private lateinit var manager: ChainMonitorCheckManager
    private var ids = 0

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = ChainMonitorRepository(
            db.chainMonitorDao(), ChangeTracker.forDatabase(db, "dev") { now },
            clock = { now }, idGenerator = { "m${ids++}" }
        )
        manager = ChainMonitorCheckManager(repository, fetcher, notifier, scheduler) { now }
    }

    @After
    fun tearDown() = db.close()

    private fun monitor(holder: String = holderA, threshold: String = "0", interval: Int = 15) =
        repository.create("Vault $holder".take(12), holder, "0x" + "b".repeat(40), "USDC", 6, threshold, interval)

    private fun balance(holder: String, value: String) {
        fetcher.balances[holder] = BigDecimal(value)
    }

    @Test
    fun `a monitor already above its threshold notifies on the first check`() {
        val m = monitor()
        balance(holderA, "12.5")

        manager.check()

        assertEquals(listOf(FiredMonitor(m, BigDecimal("12.5"))), notifier.fired)
        val state = repository.getStatesSync().single()
        assertEquals(false, state.armed)
        assertEquals("12.5", state.lastBalance)
        assertEquals(now, state.lastCheckedAt)
    }

    @Test
    fun `a balance of zero does not notify a greater than zero monitor`() {
        monitor()
        balance(holderA, "0")

        manager.check()

        assertTrue(notifier.fired.isEmpty())
        assertEquals(true, repository.getStatesSync().single().armed)
    }

    @Test
    fun `it notifies once while the balance stays above and again after it dropped back`() {
        monitor()
        balance(holderA, "1")
        manager.check()
        now += 15 * minute
        manager.check()
        assertEquals(1, notifier.fired.size)

        now += 15 * minute
        balance(holderA, "0")
        manager.check()
        now += 15 * minute
        balance(holderA, "2")
        manager.check()

        assertEquals(2, notifier.fired.size)
    }

    @Test
    fun `a monitor is only fetched when its interval has elapsed`() {
        monitor(holderA, interval = 30)
        balance(holderA, "0")
        manager.check()
        fetcher.fetched.clear()

        now += 10 * minute
        manager.check()
        assertTrue(fetcher.fetched.isEmpty())

        now += 20 * minute
        manager.check()
        assertEquals(listOf(holderA), fetcher.fetched)
    }

    @Test
    fun `a forced check fetches monitors that are not due`() {
        monitor(holderA, interval = 30)
        balance(holderA, "0")
        manager.check()
        fetcher.fetched.clear()

        now += minute
        manager.check(force = true)

        assertEquals(listOf(holderA), fetcher.fetched)
    }

    @Test
    fun `a failed fetch leaves that monitor untouched and does not block the others`() {
        monitor(holderA)
        val second = monitor(holderB)
        fetcher.balances[holderA] = IOException("boom")
        balance(holderB, "4")

        manager.check()

        assertEquals(listOf(second.id), notifier.fired.map { it.monitor.id })
        assertEquals(listOf(second.id), repository.getStatesSync().map { it.monitorId })
    }

    @Test
    fun `no internet stops fetching the remaining monitors`() {
        monitor(holderA)
        monitor(holderB)
        fetcher.balances[holderA] = RpcException.NoNetwork(null)
        balance(holderB, "4")

        manager.check()

        assertEquals(listOf(holderA), fetcher.fetched)
        assertTrue(notifier.fired.isEmpty())
    }

    @Test
    fun `a failed monitor is retried at the next tick instead of waiting its whole interval`() {
        monitor(holderA, interval = 30)
        fetcher.balances[holderA] = RpcException.Timeout(null)
        manager.check()
        fetcher.fetched.clear()
        balance(holderA, "3")

        now += 15 * minute
        manager.check()

        assertEquals(1, notifier.fired.size)
    }

    @Test
    fun `states are saved before the notification is posted`() {
        monitor()
        balance(holderA, "9")
        var statesAtNotify = -1
        notifier.onNotify = { statesAtNotify = repository.getStatesSync().size }

        manager.check()

        assertEquals(1, statesAtNotify)
    }

    @Test
    fun `an edited monitor is seeded again and fires if still above`() {
        val m = monitor()
        balance(holderA, "9")
        manager.check()
        assertEquals(1, notifier.fired.size)

        repository.update(m.copy(threshold = "5"))
        now += 15 * minute
        manager.check()

        assertEquals(2, notifier.fired.size)
    }

    @Test
    fun `rows pair each monitor with its last known balance`() {
        val m = monitor()
        val other = monitor(holderB)
        balance(holderA, "12.5")
        fetcher.balances[holderB] = IOException("down")
        manager.check()

        val rows = manager.rows()

        assertEquals(listOf(MonitorRow(m, "12.5"), MonitorRow(other, null)), rows)
    }

    @Test
    fun `the last balance of an edited monitor is not shown for its new settings`() {
        val m = monitor()
        balance(holderA, "12.5")
        manager.check()

        repository.update(m.copy(holderAddress = holderB))

        assertNull(manager.rows().single().lastBalance)
    }

    @Test
    fun `the alarm arms the next tick at the shortest interval, then checks`() {
        monitor(holderA, interval = 30)
        monitor(holderB, interval = 15)
        balance(holderA, "0")
        balance(holderB, "0")

        manager.onAlarm()

        assertEquals(listOf(now + 15 * minute), scheduler.scheduled)
        assertEquals(2, fetcher.fetched.size)
    }

    @Test
    fun `the alarm with only slow monitors ticks every 30 minutes`() {
        monitor(holderA, interval = 30)
        balance(holderA, "0")

        manager.onAlarm()

        assertEquals(listOf(now + 30 * minute), scheduler.scheduled)
    }

    @Test
    fun `the alarm without monitors stops the alarm chain`() {
        manager.onAlarm()

        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun `the alarm swallows failures after arming the next one`() {
        monitor()
        notifier.onNotify = { error("notification failed") }
        balance(holderA, "9")

        manager.onAlarm()

        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun `ensureScheduled arms a default tick without touching the database`() {
        manager.ensureScheduled()

        assertEquals(listOf(now + 15 * minute), scheduler.scheduled)
    }

    @Test
    fun `refreshSchedule arms the alarm when monitors exist and cancels it when none do`() {
        manager.refreshSchedule()
        assertEquals(1, scheduler.cancelled)

        monitor(holderA, interval = 30)
        manager.refreshSchedule()

        assertEquals(listOf(now + 30 * minute), scheduler.scheduled)
    }
}
