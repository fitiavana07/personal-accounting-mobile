package dev.fitiavana.accounting.features.balances

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YieldCalculatorTest {

    private fun project(
        apr: Double?,
        balance: Long,
        instrumentBalance: Long? = null,
        intermediaryBalance: Long? = null,
        period: YieldPeriod = YieldPeriod.YEARLY
    ) = YieldCalculator.project(apr, balance, instrumentBalance, intermediaryBalance, period)

    // --- no yield ---

    @Test
    fun `no APR means no projection`() {
        assertNull(project(apr = null, balance = 1_000_000L))
    }

    @Test
    fun `a zero APR means no projection`() {
        assertNull(project(apr = 0.0, balance = 1_000_000L))
    }

    @Test
    fun `a negative APR means no projection`() {
        assertNull(project(apr = -1.0, balance = 1_000_000L))
    }

    // --- periods ---

    @Test
    fun `yearly interest is balance times APR`() {
        assertEquals(100_000L, project(apr = 10.0, balance = 1_000_000L)?.base)
    }

    @Test
    fun `monthly interest is a twelfth of the yearly interest`() {
        assertEquals(12_000L, project(apr = 12.0, balance = 1_200_000L, period = YieldPeriod.MONTHLY)?.base)
    }

    @Test
    fun `daily interest is a 365th of the yearly interest`() {
        assertEquals(100L, project(apr = 3.65, balance = 1_000_000L, period = YieldPeriod.DAILY)?.base)
    }

    @Test
    fun `fractional APR is supported`() {
        assertEquals(55_000L, project(apr = 5.5, balance = 1_000_000L)?.base)
    }

    // --- instruments ---

    @Test
    fun `the instrument balance earns the same APR in its own units`() {
        val result = project(apr = 10.0, balance = 1_000_000L, instrumentBalance = 150_000L)

        assertEquals(100_000L, result?.base)
        assertEquals(15_000L, result?.instrument)
    }

    @Test
    fun `the intermediary balance earns the same APR in its own units`() {
        val result = project(
            apr = 10.0, balance = 1_000_000L, instrumentBalance = 150_000L, intermediaryBalance = 50_000L
        )

        assertEquals(5_000L, result?.intermediary)
    }

    @Test
    fun `accounts without an instrument have no instrument or intermediary interest`() {
        val result = project(apr = 10.0, balance = 1_000_000L)

        assertNull(result?.instrument)
        assertNull(result?.intermediary)
    }

    @Test
    fun `an intermediary without an instrument is ignored`() {
        // The intermediary instrument only exists next to an instrument, but the calculator does not depend on that.
        val result = project(apr = 10.0, balance = 1_000_000L, instrumentBalance = null, intermediaryBalance = 50_000L)

        assertEquals(5_000L, result?.intermediary)
        assertNull(result?.instrument)
    }

    // --- rounding and edge cases ---

    @Test
    fun `interest is rounded to the nearest unit`() {
        // 1,000,000 x 1% / 365 = 27.39
        assertEquals(27L, project(apr = 1.0, balance = 1_000_000L, period = YieldPeriod.DAILY)?.base)
        // 1,000 x 10% / 365 = 0.27 rounds down to zero
        assertEquals(0L, project(apr = 10.0, balance = 1_000L, period = YieldPeriod.DAILY)?.base)
        // 1,000 x 20% / 365 = 0.55 rounds up
        assertEquals(1L, project(apr = 20.0, balance = 1_000L, period = YieldPeriod.DAILY)?.base)
    }

    @Test
    fun `a zero balance projects zero rather than nothing`() {
        val result = project(apr = 10.0, balance = 0L, instrumentBalance = 0L)

        assertNotNull(result)
        assertEquals(0L, result?.base)
        assertEquals(0L, result?.instrument)
    }

    @Test
    fun `a negative balance projects negative interest`() {
        assertEquals(-100_000L, project(apr = 10.0, balance = -1_000_000L)?.base)
    }

    @Test
    fun `period fractions of a year`() {
        assertEquals(1.0, YieldPeriod.YEARLY.yearFraction, 0.0)
        assertEquals(1.0 / 12, YieldPeriod.MONTHLY.yearFraction, 1e-12)
        assertEquals(1.0 / 365, YieldPeriod.DAILY.yearFraction, 1e-12)
    }
}
