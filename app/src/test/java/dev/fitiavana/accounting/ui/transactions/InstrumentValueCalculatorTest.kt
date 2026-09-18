package dev.fitiavana.accounting.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstrumentValueCalculatorTest {

    @Test
    fun `computeBaseAmount derives the base amount from the account's prior rate`() {
        // Account previously held 1000 units (10.00 USD at 2 decimal places)
        // worth 400000 Ar. Applying 150 units (1.50 USD) should be 60000 Ar.
        val result = InstrumentValueCalculator.computeBaseAmount(
            instrumentAmount = 150L,
            balance = 400_000L,
            instrumentBalance = 1000L
        )

        assertEquals(60_000L, result)
    }

    @Test
    fun `computeBaseAmount rounds to the nearest long`() {
        val result = InstrumentValueCalculator.computeBaseAmount(
            instrumentAmount = 1L,
            balance = 10L,
            instrumentBalance = 3L
        )

        // 1 * 10 / 3 = 3.333... -> rounds to 3
        assertEquals(3L, result)
    }

    @Test
    fun `computeBaseAmount is null when the account has no prior instrument balance`() {
        assertNull(
            InstrumentValueCalculator.computeBaseAmount(
                instrumentAmount = 150L,
                balance = 0L,
                instrumentBalance = 0L
            )
        )
    }

    @Test
    fun `computeBaseAmount is null when the account's prior instrument balance is negative`() {
        assertNull(
            InstrumentValueCalculator.computeBaseAmount(
                instrumentAmount = 150L,
                balance = 0L,
                instrumentBalance = -1000L
            )
        )
    }
}
