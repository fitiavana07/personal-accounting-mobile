package dev.fitiavana.accounting

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.model.ExchangeRateCache
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.ui.home.HomeItemBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeItemBuilderTest {

    private fun account(
        id: String,
        name: String,
        type: String = "asset",
        instrumentCode: String? = null,
        intermediaryInstrumentCode: String? = null
    ) = Account(id = id, name = name, type = type, instrumentCode = instrumentCode, intermediaryInstrumentCode = intermediaryInstrumentCode)

    private fun balance(
        accountId: String,
        balance: Long = 0,
        instrumentBalance: Long = 0,
        intermediaryBalance: Long = 0
    ) = AccountBalance(accountId = accountId, balance = balance, instrumentBalance = instrumentBalance, intermediaryBalance = intermediaryBalance, updatedAt = 0L, createdAt = 0L)

    private val nvda = Instrument(code = "NVDA", note = "", type = "stock", decimalPlaces = 0)
    private val btc = Instrument(code = "BTC", note = "", type = "cryptocurrency", decimalPlaces = 8, coingeckoId = "bitcoin")
    private val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)

    @Test
    fun `qualifying account with crypto instrument and intermediary produces item`() {
        val result = HomeItemBuilder.build(
            balances = listOf(balance("acc1", instrumentBalance = 100_000_000, intermediaryBalance = 6_000_000)),
            accounts = listOf(account("acc1", "Crypto", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT")),
            instruments = mapOf("BTC" to btc, "USDT" to usdt),
            rates = emptyMap()
        )
        assertEquals(1, result.size)
        assertEquals("acc1", result[0].accountId)
        assertEquals(60000.0, result[0].bookValue, 0.0001)
        assertEquals("1 BTC = 60,000.0 USDT", result[0].bookRate)
        assertEquals("1.0 BTC", result[0].instrumentBalanceFormatted)
    }

    @Test
    fun `book rate is null when instrument balance is zero`() {
        val result = HomeItemBuilder.build(
            balances = listOf(balance("acc1", instrumentBalance = 0, intermediaryBalance = 6_000_000)),
            accounts = listOf(account("acc1", "Crypto", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT")),
            instruments = mapOf("BTC" to btc, "USDT" to usdt),
            rates = emptyMap()
        )
        assertNull(result[0].bookRate)
    }

    @Test
    fun `liability account is excluded`() {
        val result = HomeItemBuilder.build(
            balances = listOf(balance("acc1")),
            accounts = listOf(account("acc1", "Loan", type = "liability", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT")),
            instruments = mapOf("BTC" to btc, "USDT" to usdt),
            rates = emptyMap()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `non-cryptocurrency instrument is excluded`() {
        val result = HomeItemBuilder.build(
            balances = listOf(balance("acc1")),
            accounts = listOf(account("acc1", "Stocks", instrumentCode = "NVDA", intermediaryInstrumentCode = "USDT")),
            instruments = mapOf("NVDA" to nvda, "USDT" to usdt),
            rates = emptyMap()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `crypto account without intermediary instrument is excluded`() {
        val result = HomeItemBuilder.build(
            balances = listOf(balance("acc1")),
            accounts = listOf(account("acc1", "Crypto", instrumentCode = "BTC")),
            instruments = mapOf("BTC" to btc),
            rates = emptyMap()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `no cached rate yields null current value and gain loss`() {
        val result = HomeItemBuilder.build(
            balances = listOf(balance("acc1", instrumentBalance = 100_000_000, intermediaryBalance = 6_000_000)),
            accounts = listOf(account("acc1", "Crypto", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT")),
            instruments = mapOf("BTC" to btc, "USDT" to usdt),
            rates = emptyMap()
        )
        assertNull(result[0].currentValue)
        assertNull(result[0].gainLoss)
        assertNull(result[0].gainLossPercent)
        assertNull(result[0].currentRate)
    }

    @Test
    fun `cached rate produces current value and gain loss`() {
        val cache = ExchangeRateCache(
            pairKey = ExchangeRateCache.pairKey("BTC", "USDT"),
            instrumentCode = "BTC",
            intermediaryCode = "USDT",
            rate = 65000.0,
            fetchedAt = 12345L
        )
        val result = HomeItemBuilder.build(
            balances = listOf(balance("acc1", instrumentBalance = 100_000_000, intermediaryBalance = 6_000_000)),
            accounts = listOf(account("acc1", "Crypto", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT")),
            instruments = mapOf("BTC" to btc, "USDT" to usdt),
            rates = mapOf(cache.pairKey to cache)
        )
        assertEquals(65000.0, result[0].currentValue!!, 0.0001)
        assertEquals(5000.0, result[0].gainLoss!!, 0.0001)
        assertEquals(12345L, result[0].rateFetchedAt)
        assertEquals("1 BTC = 65,000.0 USDT", result[0].currentRate)
    }

    @Test
    fun `no cached rate yields null gain loss in Ar but exposes book value in Ar`() {
        val result = HomeItemBuilder.build(
            balances = listOf(balance("acc1", balance = 300_000, instrumentBalance = 100_000_000, intermediaryBalance = 6_000_000)),
            accounts = listOf(account("acc1", "Crypto", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT")),
            instruments = mapOf("BTC" to btc, "USDT" to usdt),
            rates = emptyMap()
        )
        assertEquals(300_000L, result[0].bookValueAr)
        assertNull(result[0].gainLossAr)
    }

    @Test
    fun `cached rate produces gain loss in Ar using balance-based exchange rate`() {
        val cache = ExchangeRateCache(
            pairKey = ExchangeRateCache.pairKey("BTC", "USDT"),
            instrumentCode = "BTC",
            intermediaryCode = "USDT",
            rate = 65000.0,
            fetchedAt = 12345L
        )
        val result = HomeItemBuilder.build(
            balances = listOf(balance("acc1", balance = 300_000, instrumentBalance = 100_000_000, intermediaryBalance = 6_000_000)),
            accounts = listOf(account("acc1", "Crypto", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT")),
            instruments = mapOf("BTC" to btc, "USDT" to usdt),
            rates = mapOf(cache.pairKey to cache)
        )
        // bookValue = 60,000 USDT for 300,000 Ar -> rate is 5 Ar/USDT; currentValue = 65,000 USDT -> 325,000 Ar
        assertEquals(300_000L, result[0].bookValueAr)
        assertEquals(325_000.0, result[0].currentValueAr!!, 0.0001)
        assertEquals(25_000.0, result[0].gainLossAr!!, 0.0001)
    }

    @Test
    fun `account with no balance row is excluded`() {
        val result = HomeItemBuilder.build(
            balances = emptyList(),
            accounts = listOf(account("acc1", "Crypto", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT")),
            instruments = mapOf("BTC" to btc, "USDT" to usdt),
            rates = emptyMap()
        )
        assertTrue(result.isEmpty())
    }
}
