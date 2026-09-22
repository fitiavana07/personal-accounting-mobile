package dev.fitiavana.accounting.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class IncomeToExpensesBuilderTest {

    @Test
    fun `average income between 0 and target computes partial percent and remaining`() {
        val result = IncomeToExpensesBuilder.build(
            monthlyNetIncomes = listOf(100_000, 150_000, 125_000),
            monthlyExpenses = 200_000
        )

        assertEquals(200_000L, result.monthlyExpenses)
        assertEquals(125_000L, result.averageMonthlyIncome)
        assertEquals(63, result.percent)
        assertEquals(75_000L, result.remaining)
    }

    @Test
    fun `average income exceeding target caps percent at 100 and remaining at 0`() {
        val result = IncomeToExpensesBuilder.build(
            monthlyNetIncomes = listOf(300_000, 400_000),
            monthlyExpenses = 200_000
        )

        assertEquals(350_000L, result.averageMonthlyIncome)
        assertEquals(100, result.percent)
        assertEquals(0L, result.remaining)
    }

    @Test
    fun `average income exactly matching target reaches 100 percent`() {
        val result = IncomeToExpensesBuilder.build(
            monthlyNetIncomes = listOf(200_000),
            monthlyExpenses = 200_000
        )

        assertEquals(100, result.percent)
        assertEquals(0L, result.remaining)
    }

    @Test
    fun `negative average net income (net loss) floors percent at 0`() {
        val result = IncomeToExpensesBuilder.build(
            monthlyNetIncomes = listOf(-50_000, -30_000),
            monthlyExpenses = 200_000
        )

        assertEquals(-40_000L, result.averageMonthlyIncome)
        assertEquals(0, result.percent)
        assertEquals(240_000L, result.remaining)
    }

    @Test
    fun `empty monthly net incomes yields zero average`() {
        val result = IncomeToExpensesBuilder.build(
            monthlyNetIncomes = emptyList(),
            monthlyExpenses = 200_000
        )

        assertEquals(0L, result.averageMonthlyIncome)
        assertEquals(0, result.percent)
        assertEquals(200_000L, result.remaining)
    }

    @Test
    fun `zero monthly expenses yields 100 percent and 0 remaining`() {
        val result = IncomeToExpensesBuilder.build(
            monthlyNetIncomes = listOf(100_000),
            monthlyExpenses = 0
        )

        assertEquals(0L, result.monthlyExpenses)
        assertEquals(100, result.percent)
        assertEquals(0L, result.remaining)
    }
}
