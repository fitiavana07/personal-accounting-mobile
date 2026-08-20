package dev.fitiavana.accounting

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.ui.balances.BalanceItem
import dev.fitiavana.accounting.ui.balances.BalanceItemBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceItemBuilderTest {

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

    // --- Basic mapping ---

    @Test
    fun `empty inputs produce empty list`() {
        assertTrue(BalanceItemBuilder.build(emptyList(), emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun `balance with no matching account is excluded`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 500)),
            accounts = emptyList(),
            instruments = emptyMap()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `balance maps to item with correct fields`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 1000, updatedAt = 9999L)),
            accounts = listOf(account("acc1", "Cash")),
            instruments = emptyMap()
        )
        assertEquals(1, result.size)
        assertEquals(BalanceItem("acc1", "Cash", 1000L, 0L, null, updatedAt = 9999L), result[0])
    }

    // --- Zero balance is included (account has transactions) ---

    @Test
    fun `zero balance account is included`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 0)),
            accounts = listOf(account("acc1", "Wash")),
            instruments = emptyMap()
        )
        assertEquals(1, result.size)
        assertEquals(0L, result[0].balance)
    }

    // --- Sorting: alphabetical by account name ---

    @Test
    fun `items sorted alphabetically by account name`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(
                balance("acc1", 1300),
                balance("acc2", 10000),
                balance("acc3", 3000)
            ),
            accounts = listOf(
                account("acc1", "Cash"),
                account("acc2", "Capital"),
                account("acc3", "Sales")
            ),
            instruments = emptyMap()
        )
        assertEquals(listOf("Capital", "Cash", "Sales"), result.map { it.accountName })
    }

    @Test
    fun `account name sort is independent of balance sign`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(
                balance("acc1", -500),
                balance("acc2", 1000),
                balance("acc3", 0)
            ),
            accounts = listOf(
                account("acc1", "Overdraft"),
                account("acc2", "Cash"),
                account("acc3", "Wash")
            ),
            instruments = emptyMap()
        )
        assertEquals(listOf("Cash", "Overdraft", "Wash"), result.map { it.accountName })
    }

    // --- Multiple accounts, some without balances ---

    // --- Instrument and intermediaryInstrument population ---

    @Test
    fun `account with instrumentCode populates instrument from map`() {
        val usd = instrument("USD")
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 500, instrumentBalance = 50)),
            accounts = listOf(account("acc1", "Cash", instrumentCode = "USD")),
            instruments = mapOf("USD" to usd)
        )
        assertEquals(usd, result[0].instrument)
        assertEquals(50L, result[0].instrumentBalance)
    }

    @Test
    fun `account with instrumentCode not found in map has null instrument`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 500)),
            accounts = listOf(account("acc1", "Cash", instrumentCode = "USD")),
            instruments = emptyMap()
        )
        assertEquals(null, result[0].instrument)
    }

    @Test
    fun `account with no instrumentCode has null instrument`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 500)),
            accounts = listOf(account("acc1", "Cash", instrumentCode = null)),
            instruments = mapOf("USD" to instrument("USD"))
        )
        assertEquals(null, result[0].instrument)
    }

    @Test
    fun `account with intermediaryInstrumentCode populates intermediaryInstrument from map`() {
        val eur = instrument("EUR")
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 500, intermediaryBalance = 200)),
            accounts = listOf(account("acc1", "Cash", intermediaryInstrumentCode = "EUR")),
            instruments = mapOf("EUR" to eur)
        )
        assertEquals(eur, result[0].intermediaryInstrument)
        assertEquals(200L, result[0].intermediaryBalance)
    }

    @Test
    fun `account with intermediaryInstrumentCode not found in map has null intermediaryInstrument`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 500)),
            accounts = listOf(account("acc1", "Cash", intermediaryInstrumentCode = "EUR")),
            instruments = emptyMap()
        )
        assertEquals(null, result[0].intermediaryInstrument)
    }

    @Test
    fun `account with both instrument codes populates both instruments`() {
        val usd = instrument("USD")
        val eur = instrument("EUR")
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 1000, instrumentBalance = 100, intermediaryBalance = 90)),
            accounts = listOf(account("acc1", "FX", instrumentCode = "USD", intermediaryInstrumentCode = "EUR")),
            instruments = mapOf("USD" to usd, "EUR" to eur)
        )
        assertEquals(usd, result[0].instrument)
        assertEquals(eur, result[0].intermediaryInstrument)
    }

    // --- Multiple accounts, some without balances ---

    @Test
    fun `accounts without balance rows are excluded`() {
        val result = BalanceItemBuilder.build(
            balances = listOf(balance("acc1", 500)),
            accounts = listOf(
                account("acc1", "Cash"),
                account("acc2", "NoTxAccount")
            ),
            instruments = emptyMap()
        )
        assertEquals(1, result.size)
        assertEquals("acc1", result[0].accountId)
    }
}
