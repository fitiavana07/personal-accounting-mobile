package dev.fitiavana.accounting.ui.earn

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.YieldAmounts
import dev.fitiavana.accounting.features.instruments.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarnItemBuilderTest {

    private val btc = Instrument(code = "BTC", note = "", type = "crypto", decimalPlaces = 8)
    private val usd = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)
    private val instruments = mapOf("BTC" to btc, "USD" to usd)

    private fun balance(
        id: String,
        base: Long,
        instrument: Long = 0L,
        intermediary: Long = 0L
    ) = AccountBalance(
        accountId = id, balance = base, instrumentBalance = instrument,
        intermediaryBalance = intermediary, updatedAt = 0L, createdAt = 0L
    )

    private fun build(accounts: List<Account>, balances: List<AccountBalance>) =
        EarnItemBuilder.build(accounts, balances, instruments)

    @Test
    fun `no accounts gives an empty state`() {
        val state = build(emptyList(), emptyList())

        assertTrue(state.items.isEmpty())
        assertEquals(EarnTotals(0L, 0L, 0L), state.totals)
    }

    @Test
    fun `only asset accounts with a positive APR are listed`() {
        val accounts = listOf(
            Account(id = "earn", name = "Earn", type = "asset", aprPercent = 5.0),
            Account(id = "cash", name = "Cash", type = "asset"),
            Account(id = "zero", name = "Zero", type = "asset", aprPercent = 0.0),
            Account(id = "loan", name = "Loan", type = "liability", aprPercent = 4.0)
        )

        val state = build(accounts, listOf(balance("earn", 1_000_000L)))

        assertEquals(listOf("earn"), state.items.map { it.account.id })
    }

    @Test
    fun `items are ordered by base balance, largest first`() {
        val accounts = listOf(
            Account(id = "small", name = "Small", type = "asset", aprPercent = 5.0),
            Account(id = "big", name = "Big", type = "asset", aprPercent = 5.0)
        )

        val state = build(accounts, listOf(balance("small", 10_000L), balance("big", 900_000L)))

        assertEquals(listOf("big", "small"), state.items.map { it.account.id })
    }

    @Test
    fun `an account without a balance row earns nothing but is still listed`() {
        val state = build(listOf(Account(id = "earn", name = "Earn", type = "asset", aprPercent = 5.0)), emptyList())

        val item = state.items.single()
        assertEquals(0L, item.balance)
        assertEquals(0L, item.yearly.base)
    }

    @Test
    fun `base interest uses the base balance for each period`() {
        val accounts = listOf(Account(id = "earn", name = "Earn", type = "asset", aprPercent = 12.0))

        val item = build(accounts, listOf(balance("earn", 1_200_000L))).items.single()

        assertEquals(144_000L, item.yearly.base)
        assertEquals(12_000L, item.monthly.base)
        assertEquals(395L, item.daily.base) // 1,200,000 x 12% / 365 = 394.5
    }

    @Test
    fun `instrument interest uses the instrument balance in its own units`() {
        val accounts = listOf(
            Account(id = "earn", name = "Earn", type = "asset", instrumentCode = "BTC", aprPercent = 10.0)
        )

        val item = build(accounts, listOf(balance("earn", 5_000_000L, instrument = 200_000_000L))).items.single()

        assertEquals(500_000L, item.yearly.base)
        assertEquals(20_000_000L, item.yearly.instrument) // 2 BTC x 10% = 0.2 BTC
        assertNull(item.yearly.intermediary)
        assertEquals(btc, item.instrument)
    }

    @Test
    fun `intermediary interest uses the intermediary balance`() {
        val accounts = listOf(
            Account(
                id = "earn", name = "Earn", type = "asset", instrumentCode = "BTC",
                intermediaryInstrumentCode = "USD", aprPercent = 10.0
            )
        )

        val item = build(
            accounts,
            listOf(balance("earn", 5_000_000L, instrument = 200_000_000L, intermediary = 120_000L))
        ).items.single()

        assertEquals(12_000L, item.yearly.intermediary) // 1,200.00 USD x 10% = 120.00 USD
        assertEquals(usd, item.intermediaryInstrument)
    }

    @Test
    fun `an unknown instrument code gives no instrument interest`() {
        val accounts = listOf(
            Account(id = "earn", name = "Earn", type = "asset", instrumentCode = "XYZ", aprPercent = 10.0)
        )

        val item = build(accounts, listOf(balance("earn", 1_000_000L, instrument = 500L))).items.single()

        assertNull(item.instrument)
        assertNull(item.yearly.instrument)
    }

    @Test
    fun `totals add up the base interest of every listed account`() {
        val accounts = listOf(
            Account(id = "a", name = "A", type = "asset", aprPercent = 12.0),
            Account(id = "b", name = "B", type = "asset", aprPercent = 6.0)
        )

        val state = build(accounts, listOf(balance("a", 1_200_000L), balance("b", 1_200_000L)))

        assertEquals(EarnTotals(daily = 395L + 197L, monthly = 12_000L + 6_000L, yearly = 144_000L + 72_000L), state.totals)
    }

    @Test
    fun `the item exposes its three projections`() {
        val accounts = listOf(Account(id = "earn", name = "Earn", type = "asset", aprPercent = 10.0))

        val item = build(accounts, listOf(balance("earn", 3_650_000L))).items.single()

        assertEquals(YieldAmounts(base = 1_000L, instrument = null, intermediary = null), item.daily)
    }
}
