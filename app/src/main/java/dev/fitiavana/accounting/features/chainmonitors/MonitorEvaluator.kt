package dev.fitiavana.accounting.features.chainmonitors

import java.math.BigDecimal

/** A monitor whose balance went above its threshold, with that balance in token units. */
data class FiredMonitor(val monitor: ChainMonitor, val balance: BigDecimal)

/** What a check decided: the monitors to notify, and the states that changed and must be saved. */
data class MonitorEvaluation(val fired: List<FiredMonitor>, val states: List<MonitorState>)

/**
 * Decides which monitors fire. A monitor fires once when its balance is greater than the threshold while armed,
 * then stays disarmed until the balance is back at or below the threshold. Unlike gain/loss alerts a monitor
 * without a usable state is seeded armed, so a balance that is already above the threshold fires on the first check.
 */
object MonitorEvaluator {
    /** A tick that arrives this much early still counts as due: alarms are inexact. */
    private const val EARLY_SLACK_MILLIS = 60_000L

    fun evaluate(
        monitors: List<ChainMonitor>,
        balances: Map<String, BigDecimal?>,
        states: Map<String, MonitorState>,
        now: Long
    ): MonitorEvaluation {
        val fired = mutableListOf<FiredMonitor>()
        val changed = mutableListOf<MonitorState>()
        for (monitor in monitors) {
            val balance = balances[monitor.id] ?: continue
            val armed = states[monitor.id]?.takeIf { it.signature == monitor.signature }?.armed ?: true
            val above = balance > monitor.thresholdValue
            if (armed && above) fired += FiredMonitor(monitor, balance)
            val nowArmed = if (armed) !above else balance <= monitor.thresholdValue
            changed += MonitorState(monitor.id, nowArmed, monitor.signature, now, balance.toPlainString())
        }
        return MonitorEvaluation(fired, changed)
    }

    fun isDue(monitor: ChainMonitor, state: MonitorState?, now: Long): Boolean {
        if (state == null || state.signature != monitor.signature) return true
        return now - state.lastCheckedAt >= monitor.intervalMinutes * 60_000L - EARLY_SLACK_MILLIS
    }
}
