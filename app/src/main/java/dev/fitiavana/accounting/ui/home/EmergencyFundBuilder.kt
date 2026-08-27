package dev.fitiavana.accounting.ui.home

import kotlin.math.roundToInt

data class EmergencyFundInfo(
    val monthlyExpenses: Long,
    val sixMonthTarget: Long,
    val sixMonthPercent: Int,
    val sixMonthRemaining: Long
)

object EmergencyFundBuilder {
    fun build(totalAssets: Long, monthlyExpenses: Long): EmergencyFundInfo {
        val sixMonthTarget = monthlyExpenses * 6
        return EmergencyFundInfo(
            monthlyExpenses = monthlyExpenses,
            sixMonthTarget = sixMonthTarget,
            sixMonthPercent = percentReached(totalAssets, sixMonthTarget),
            sixMonthRemaining = (sixMonthTarget - totalAssets).coerceAtLeast(0)
        )
    }

    private fun percentReached(totalAssets: Long, target: Long): Int {
        if (target <= 0) return 100
        val percent = (totalAssets.toDouble() / target * 100).roundToInt()
        return percent.coerceIn(0, 100)
    }
}
