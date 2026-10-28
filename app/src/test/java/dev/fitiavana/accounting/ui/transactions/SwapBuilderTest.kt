package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapBuilderTest {

    private val cash = Account(id = "cash", name = "Cash", type = "asset")
    private val usdWallet = Account(id = "usd", name = "USD Wallet", type = "asset", instrumentCode = "USD")
    private val usdSavings = Account(id = "usd2", name = "USD Savings", type = "asset", instrumentCode = "USD")
    private val eurWallet = Account(id = "eur", name = "EUR Wallet", type = "asset", instrumentCode = "EUR")
    private val btcViaUsd = Account(
        id = "btc", name = "BTC", type = "asset", instrumentCode = "BTC", intermediaryInstrumentCode = "USD"
    )
    private val ethViaUsd = btcViaUsd.copy(id = "eth", name = "ETH", instrumentCode = "ETH")
    private val btcViaEur = btcViaUsd.copy(id = "btc_eur", intermediaryInstrumentCode = "EUR")
    private val usdViaUsd = usdWallet.copy(id = "usd_via", intermediaryInstrumentCode = "USD")
    private val revenue = Account(id = "rev", name = "Revenue", type = "revenue")

    private val all = listOf(cash, usdWallet, usdSavings, eurWallet, btcViaUsd, ethViaUsd, btcViaEur, usdViaUsd, revenue)
    private val funded = all.associate { it.id to 1L }

    @Test
    fun `selectableFromAccounts offers instrument assets without intermediary that have a swap target`() {
        assertEquals(
            listOf(usdWallet, usdSavings, eurWallet),
            SwapBuilder.selectableFromAccounts(all, funded)
        )
    }

    @Test
    fun `selectableFromAccounts hides accounts without a target`() {
        val accounts = listOf(usdWallet, eurWallet, btcViaUsd)

        assertEquals(listOf(usdWallet), SwapBuilder.selectableFromAccounts(accounts, funded))
    }

    @Test
    fun `selectableFromAccounts hides zero or unknown balances`() {
        assertEquals(
            listOf(usdSavings),
            SwapBuilder.selectableFromAccounts(all, mapOf("usd" to 0L, "usd2" to 5L))
        )
    }

    @Test
    fun `selectableToAccounts matches intermediary to from instrument with a different instrument`() {
        assertEquals(listOf(btcViaUsd, ethViaUsd), SwapBuilder.selectableToAccounts(all, usdWallet))
        assertEquals(listOf(btcViaEur), SwapBuilder.selectableToAccounts(all, eurWallet))
    }

    @Test
    fun `selectableToAccounts excludes an account whose instrument equals the from instrument`() {
        assertEquals(emptyList<Account>(), SwapBuilder.selectableToAccounts(listOf(usdViaUsd), usdWallet))
    }

    @Test
    fun `selectableToAccounts is empty without a from account`() {
        assertEquals(emptyList<Account>(), SwapBuilder.selectableToAccounts(all, null))
    }

    @Test
    fun `buildEntries credits from and debits to carrying the from amount as intermediary`() {
        val entries = SwapBuilder.buildEntries("usd", "btc", fromAmount = 100L, toAmount = 3L, baseAmount = 450L)

        assertEquals("usd", entries[0].accountId)
        assertEquals(450L, entries[0].creditAmount)
        assertNull(entries[0].debitAmount)
        assertEquals(100L, entries[0].instrumentCreditAmount)
        assertNull(entries[0].intermediaryCreditAmount)

        assertEquals("btc", entries[1].accountId)
        assertEquals(450L, entries[1].debitAmount)
        assertNull(entries[1].creditAmount)
        assertEquals(3L, entries[1].instrumentDebitAmount)
        assertEquals(100L, entries[1].intermediaryDebitAmount)
    }

    @Test
    fun `buildEntries yields null amounts for non-positive values`() {
        val entries = SwapBuilder.buildEntries("usd", "btc", fromAmount = 0L, toAmount = 3L, baseAmount = null)

        assertNull(entries[0].creditAmount)
        assertNull(entries[0].instrumentCreditAmount)
        assertNull(entries[1].debitAmount)
        assertNull(entries[1].intermediaryDebitAmount)
    }

    @Test
    fun `buildEntries pass the validator`() {
        val entries = SwapBuilder.buildEntries("usd", "btc", fromAmount = 100L, toAmount = 3L, baseAmount = 450L)

        assertTrue(TransactionValidator.validate(entries) is TransactionValidator.ValidationResult.Valid)
    }
}
