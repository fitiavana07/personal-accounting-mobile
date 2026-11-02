package dev.fitiavana.accounting.features.chainmonitors

import dev.fitiavana.accounting.network.evm.Erc20BalanceFetcher
import dev.fitiavana.accounting.network.evm.RpcException
import java.math.BigDecimal

/** Arms (and disarms) the single alarm that triggers the next monitor check. */
interface MonitorScheduler {
    fun scheduleAt(triggerAtMillis: Long)
    fun cancel()
}

/** Tells the user a monitor's balance went above its threshold. */
interface MonitorNotifier {
    fun notifyFired(fired: FiredMonitor)
}

/**
 * A monitor with the last balance read for it, in token units, and when it was read (epoch millis).
 * Both are null if none was read yet; a failed fetch leaves them at the last successful reading.
 */
data class MonitorRow(val monitor: ChainMonitor, val lastBalance: String?, val lastCheckedAt: Long? = null)

/**
 * Checks the chain monitors. One alarm ticks at the shortest monitor interval and [check] fetches only the
 * monitors that are due, so a 30 minute monitor is not fetched at every 15 minute tick. A failed fetch leaves
 * the monitor's state alone (it stays due, so the next tick retries) and never notifies; no internet stops
 * the remaining fetches of that run. Everything is synchronous: call it off the main thread.
 */
class ChainMonitorCheckManager(
    private val repository: ChainMonitorRepository,
    private val fetcher: Erc20BalanceFetcher,
    private val notifier: MonitorNotifier,
    private val scheduler: MonitorScheduler,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Fetches the due monitors ([force]: all of them), saves the new states, then notifies the ones that fired. */
    @Synchronized
    fun check(force: Boolean = false) {
        val monitors = repository.getAllSync()
        if (monitors.isEmpty()) return
        val now = clock()
        val states = repository.getStatesSync().associateBy { it.monitorId }
        val balances = mutableMapOf<String, BigDecimal?>()
        for (monitor in monitors) {
            if (!force && !MonitorEvaluator.isDue(monitor, states[monitor.id], now)) continue
            try {
                balances[monitor.id] =
                    fetcher.fetchBalance(monitor.holderAddress, monitor.tokenAddress, monitor.tokenDecimals)
            } catch (e: RpcException.NoNetwork) {
                break
            } catch (e: Exception) {
                // This monitor stays due: the next tick tries again.
            }
        }
        val result = MonitorEvaluator.evaluate(monitors, balances, states, now)
        // Saved before notifying, so a crash in between cannot notify the same crossing twice.
        if (result.states.isNotEmpty()) repository.saveStates(result.states)
        result.fired.forEach { notifier.notifyFired(it) }
    }

    /** Every monitor with its last known balance, from the saved states: no network. */
    fun rows(): List<MonitorRow> {
        val states = repository.getStatesSync().associateBy { it.monitorId }
        return repository.getAllSync().map { monitor ->
            val state = states[monitor.id]?.takeIf { it.signature == monitor.signature }
            MonitorRow(monitor, state?.lastBalance, state?.lastCheckedAt)
        }
    }

    /** The alarm fired: arm the next tick first (none when no monitor is left), then check. */
    fun onAlarm() {
        val interval = try {
            nextTickMillis(repository.getAllSync())
        } catch (e: Exception) {
            DEFAULT_TICK_MILLIS
        }
        if (interval == null) {
            scheduler.cancel()
            return
        }
        scheduler.scheduleAt(clock() + interval)
        try {
            check()
        } catch (e: Exception) {
            // The next alarm tries again.
        }
    }

    /** Boot: re-arms the alarm, which a reboot clears. No database access, so it is safe on the main thread. */
    fun ensureScheduled() {
        scheduler.scheduleAt(clock() + DEFAULT_TICK_MILLIS)
    }

    /** After a monitor was saved or deleted: arms the alarm at the shortest interval, or cancels it with none left. */
    fun refreshSchedule() {
        val interval = nextTickMillis(repository.getAllSync())
        if (interval == null) scheduler.cancel() else scheduler.scheduleAt(clock() + interval)
    }

    private fun nextTickMillis(monitors: List<ChainMonitor>): Long? =
        monitors.minOfOrNull { it.intervalMinutes }?.let { it * 60_000L }

    companion object {
        const val DEFAULT_TICK_MILLIS = 15L * 60 * 1000
    }
}
