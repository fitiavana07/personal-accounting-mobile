package dev.fitiavana.accounting.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyFundBuilderTest {

    @Test
    fun `total assets between 0 and target computes partial percent and remaining`() {
        val result = EmergencyFundBuilder.build(
            totalAssets = 150_000,
            monthlyExpenses = 100_000
        )

        assertEquals(100_000L, result.monthlyExpenses)
        assertEquals(600_000L, result.sixMonthTarget)
        assertEquals(25, result.sixMonthPercent)
        assertEquals(450_000L, result.sixMonthRemaining)
    }

    @Test
    fun `total assets exceeding target caps percent at 100 and remaining at 0`() {
        val result = EmergencyFundBuilder.build(
            totalAssets = 1_000_000,
            monthlyExpenses = 100_000
        )

        assertEquals(600_000L, result.sixMonthTarget)
        assertEquals(100, result.sixMonthPercent)
        assertEquals(0L, result.sixMonthRemaining)
    }

    @Test
    fun `zero monthly expenses yields zero target, 100 percent and 0 remaining`() {
        val result = EmergencyFundBuilder.build(
            totalAssets = 500_000,
            monthlyExpenses = 0
        )

        assertEquals(0L, result.monthlyExpenses)
        assertEquals(0L, result.sixMonthTarget)
        assertEquals(100, result.sixMonthPercent)
        assertEquals(0L, result.sixMonthRemaining)
    }

    @Test
    fun `total assets exactly matching target reaches 100 percent`() {
        val result = EmergencyFundBuilder.build(
            totalAssets = 600_000,
            monthlyExpenses = 100_000
        )

        assertEquals(600_000L, result.sixMonthTarget)
        assertEquals(100, result.sixMonthPercent)
        assertEquals(0L, result.sixMonthRemaining)
    }
}
