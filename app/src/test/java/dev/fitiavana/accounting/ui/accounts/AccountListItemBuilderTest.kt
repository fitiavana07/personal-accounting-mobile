package dev.fitiavana.accounting.ui.accounts

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.instruments.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountListItemBuilderTest {

    private fun account(
        id: String,
        name: String,
        instrumentCode: String? = null,
        intermediaryInstrumentCode: String? = null
    ) = Account(id = id, name = name, type = "asset", instrumentCode = instrumentCode, intermediaryInstrumentCode = intermediaryInstrumentCode)

    private fun balance(
        accountId: String,
        balance: Long,
        instrumentBalance: Long = 0L,
        intermediaryBalance: Long = 0L,
        updatedAt: Long = 1000L
    ) = AccountBalance(accountId = accountId, balance = balance, instrumentBalance = instrumentBalance, intermediaryBalance = intermediaryBalance, updatedAt = updatedAt, createdAt = 0L)

    private fun instrument(code: String) = Instrument(code = code, note = "", type = "currency", decimalPlaces = 2)

    @Test
    fun `empty inputs produce empty list`() {
        assertTrue(AccountListItemBuilder.build(emptyList(), emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun `account without a balance row is still included with zero balance and null updatedAt`() {
        val result = AccountListItemBuilder.build(
            accounts = listOf(account("acc1", "New Account")),
            balances = emptyList(),
            instruments = emptyMap()
        )
        assertEquals(1, result.size)
        assertEquals(0L, result[0].balance)
        assertEquals(0L, result[0].instrumentBalance)
        assertEquals(0L, result[0].intermediaryBalance)
        assertNull(result[0].updatedAt)
    }

    @Test
    fun `account with a balance row populates balance fields`() {
        val result = AccountListItemBuilder.build(
            accounts = listOf(account("acc1", "Cash")),
            balances = listOf(balance("acc1", 1000, updatedAt = 9999L)),
            instruments = emptyMap()
        )
        assertEquals(1000L, result[0].balance)
        assertEquals(9999L, result[0].updatedAt)
    }

    @Test
    fun `preserves input account order`() {
        val result = AccountListItemBuilder.build(
            accounts = listOf(
                account("acc2", "Capital"),
                account("acc1", "Cash"),
                account("acc3", "Sales")
            ),
            balances = emptyList(),
            instruments = emptyMap()
        )
        assertEquals(listOf("Capital", "Cash", "Sales"), result.map { it.account.name })
    }

    @Test
    fun `balance with no matching account is ignored`() {
        val result = AccountListItemBuilder.build(
            accounts = listOf(account("acc1", "Cash")),
            balances = listOf(balance("acc1", 500), balance("stale", 999)),
            instruments = emptyMap()
        )
        assertEquals(1, result.size)
        assertEquals(500L, result[0].balance)
    }

    @Test
    fun `account with instrumentCode populates instrument from map regardless of balance row`() {
        val usd = instrument("USD")
        val result = AccountListItemBuilder.build(
            accounts = listOf(account("acc1", "Cash", instrumentCode = "USD")),
            balances = emptyList(),
            instruments = mapOf("USD" to usd)
        )
        assertEquals(usd, result[0].instrument)
    }

    @Test
    fun `account with instrumentCode not found in map has null instrument`() {
        val result = AccountListItemBuilder.build(
            accounts = listOf(account("acc1", "Cash", instrumentCode = "USD")),
            balances = emptyList(),
            instruments = emptyMap()
        )
        assertNull(result[0].instrument)
    }

    @Test
    fun `account with both instrument codes populates both instruments and their balances`() {
        val usd = instrument("USD")
        val eur = instrument("EUR")
        val result = AccountListItemBuilder.build(
            accounts = listOf(account("acc1", "FX", instrumentCode = "USD", intermediaryInstrumentCode = "EUR")),
            balances = listOf(balance("acc1", 1000, instrumentBalance = 100, intermediaryBalance = 90)),
            instruments = mapOf("USD" to usd, "EUR" to eur)
        )
        assertEquals(usd, result[0].instrument)
        assertEquals(100L, result[0].instrumentBalance)
        assertEquals(eur, result[0].intermediaryInstrument)
        assertEquals(90L, result[0].intermediaryBalance)
    }
}
