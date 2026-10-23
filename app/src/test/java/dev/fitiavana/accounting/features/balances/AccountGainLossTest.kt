package dev.fitiavana.accounting.features.balances

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.instruments.Instrument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountGainLossTest {

    private val btc = Instrument(code = "BTC", note = "", type = "cryptocurrency", decimalPlaces = 8, coingeckoId = "bitcoin")
    private val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)
    private val instruments = mapOf("BTC" to btc, "USDT" to usdt)

    private fun account(id: String, type: String = "asset") = Account(
        id = id, name = id, type = type,
        instrumentCode = "BTC", intermediaryInstrumentCode = "USDT"
    )

    private fun balance(accountId: String, balance: Long = 300_000) = AccountBalance(
        accountId = accountId, balance = balance,
        instrumentBalance = 100_000_000, intermediaryBalance = 6_000_000,
        updatedAt = 0L, createdAt = 0L
    )

    private fun rate(value: Double) = ExchangeRateCache(
        pairKey = ExchangeRateCache.pairKey("BTC", "USDT"),
        instrumentCode = "BTC", intermediaryCode = "USDT",
        rate = value, fetchedAt = 1L
    )

    @Test
    fun `percent is computed from the cached rate`() {
        val r = rate(66_000.0)
        val result = AccountGainLoss.percentByAccount(
            listOf(balance("a")), listOf(account("a")), instruments, mapOf(r.pairKey to r)
        )
        assertEquals(10.0, result["a"]!!, 0.0001)
    }

    @Test
    fun `percent is negative for a loss`() {
        val r = rate(54_000.0)
        val result = AccountGainLoss.percentByAccount(
            listOf(balance("a")), listOf(account("a")), instruments, mapOf(r.pairKey to r)
        )
        assertEquals(-10.0, result["a"]!!, 0.0001)
    }

    @Test
    fun `eligible account without cached rate maps to null`() {
        val result = AccountGainLoss.percentByAccount(
            listOf(balance("a")), listOf(account("a")), instruments, emptyMap()
        )
        assertTrue(result.containsKey("a"))
        assertNull(result["a"])
    }

    @Test
    fun `ineligible accounts are absent`() {
        val result = AccountGainLoss.percentByAccount(
            listOf(balance("liab"), balance("small", balance = 9_999)),
            listOf(account("liab", type = "liability"), account("small")),
            instruments,
            emptyMap()
        )
        assertTrue(result.isEmpty())
    }
}
