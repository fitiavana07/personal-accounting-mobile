package dev.fitiavana.accounting.features.alerts

/** An alert that crossed its threshold, with the gain/loss % that crossed it. */
data class FiredAlert(val alert: GainLossAlert, val percent: Double)

/** What a check decided: the alerts to notify, and the states that changed and must be saved. */
data class AlertEvaluation(val fired: List<FiredAlert>, val states: List<AlertState>)

/**
 * Decides which alerts fire. An alert fires once when its account's gain/loss % reaches the threshold
 * while armed, then stays disarmed until the % moves [HYSTERESIS] points back past the threshold.
 * An alert without a usable state is seeded from the current % and never fires on that run.
 */
object AlertEvaluator {
    /** Percentage points the gain/loss % must move back past the threshold to re-arm. */
    const val HYSTERESIS = 1.0

    fun evaluate(
        alerts: List<GainLossAlert>,
        percents: Map<String, Double?>,
        states: Map<String, AlertState>
    ): AlertEvaluation {
        val fired = mutableListOf<FiredAlert>()
        val changed = mutableListOf<AlertState>()
        for (alert in alerts) {
            val percent = percents[alert.accountId] ?: continue
            val state = states[alert.id]?.takeIf { it.signature == alert.signature }
            if (state == null) {
                changed += AlertState(alert.id, !isReached(alert, percent), alert.signature)
            } else if (state.armed && isReached(alert, percent)) {
                fired += FiredAlert(alert, percent)
                changed += state.copy(armed = false)
            } else if (!state.armed && isClearOfThreshold(alert, percent)) {
                changed += state.copy(armed = true)
            }
        }
        return AlertEvaluation(fired, changed)
    }

    private fun isReached(alert: GainLossAlert, percent: Double): Boolean =
        if (alert.type == AlertTypes.CROSSING_ABOVE) percent >= alert.thresholdPercent
        else percent <= alert.thresholdPercent

    private fun isClearOfThreshold(alert: GainLossAlert, percent: Double): Boolean =
        if (alert.type == AlertTypes.CROSSING_ABOVE) percent <= alert.thresholdPercent - HYSTERESIS
        else percent >= alert.thresholdPercent + HYSTERESIS
}
