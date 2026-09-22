package dev.fitiavana.accounting.ui.home

import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class IncomeToExpensesInfo(
    val monthlyExpenses: Long,
    val averageMonthlyIncome: Long,
    val percent: Int,
    val remaining: Long
)

/** Home screen's "Income to Expenses" progress section: average monthly net income vs. monthly expenses. */
object IncomeToExpensesBuilder {

    fun build(monthlyNetIncomes: List<Long>, monthlyExpenses: Long): IncomeToExpensesInfo {
        val average = if (monthlyNetIncomes.isEmpty()) {
            0L
        } else {
            monthlyNetIncomes.average().roundToLong()
        }

        return IncomeToExpensesInfo(
            monthlyExpenses = monthlyExpenses,
            averageMonthlyIncome = average,
            percent = percentReached(average, monthlyExpenses),
            remaining = (monthlyExpenses - average).coerceAtLeast(0)
        )
    }

    private fun percentReached(average: Long, target: Long): Int {
        if (target <= 0) return 100
        val percent = (average.toDouble() / target * 100).roundToInt()
        return percent.coerceIn(0, 100)
    }
}
