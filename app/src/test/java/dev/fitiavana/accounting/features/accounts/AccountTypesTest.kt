package dev.fitiavana.accounting.features.accounts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTypesTest {

    @Test
    fun `asset liability and equity support instruments`() {
        assertTrue(AccountTypes.supportsInstrument(AccountTypes.ASSET))
        assertTrue(AccountTypes.supportsInstrument(AccountTypes.LIABILITY))
        assertTrue(AccountTypes.supportsInstrument(AccountTypes.EQUITY))
    }

    @Test
    fun `income statement and drawing types do not support instruments`() {
        listOf(
            AccountTypes.REVENUE,
            AccountTypes.EXPENSE,
            AccountTypes.DRAWING,
            AccountTypes.GAIN,
            AccountTypes.LOSS
        ).forEach { assertFalse(it, AccountTypes.supportsInstrument(it)) }
    }
}
