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
    fun `selectableFromAccounts offers base and instrument assets without intermediary that have a swap target`() {
        assertEquals(
            listOf(cash, usdWallet, usdSavings, eurWallet),
            SwapBuilder.selectableFromAccounts(all, funded)
        )
    }

    @Test
    fun `selectableFromAccounts hides a base asset without a target`() {
        assertEquals(emptyList<Account>(), SwapBuilder.selectableFromAccounts(listOf(cash, btcViaUsd), funded))
    }

    @Test
    fun `selectableFromAccounts hides an unfunded base asset`() {
        assertEquals(listOf(usdWallet), SwapBuilder.selectableFromAccounts(all, mapOf("usd" to 1L)))
    }

    @Test
    fun `isBaseFrom is true only for an asset without instrument and intermediary`() {
        assertTrue(SwapBuilder.isBaseFrom(cash))
        assertTrue(!SwapBuilder.isBaseFrom(usdWallet))
        assertTrue(!SwapBuilder.isBaseFrom(btcViaUsd))
        assertTrue(!SwapBuilder.isBaseFrom(revenue.copy(type = "expense")))
    }

    @Test
    fun `selectableToAccounts for a base from offers instrument assets without intermediary`() {
        assertEquals(listOf(usdWallet, usdSavings, eurWallet), SwapBuilder.selectableToAccounts(all, cash))
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

    // 5.031686 USDT (6 dp) for 0.0022 ETH (4+ dp), fee 0.0000022 ETH; USDT worth 4,500 Ar per unit.
    private val fromAmount = 5_031_686L
    private val toAmount = 22_000L
    private val fee = 22L
    private val fromBalance = 4_500_000_000L
    private val fromInstrumentBalance = 1_000_000_000L

    @Test
    fun `computeFee converts the fee to from units then to base with the from rate`() {
        val result = SwapBuilder.computeFee(
            "exp", feeAmount = fee, fromAmount = fromAmount, toAmount = toAmount,
            fromBalance = fromBalance, fromInstrumentBalance = fromInstrumentBalance
        )!!

        // 22 * 5,031,686 / 22,000 = 5,031.686 -> 5,032 from units
        assertEquals(5_032L, result.feeFromAmount)
        // 5,032 * 4.5 = 22,644 base
        assertEquals(22_644L, result.feeBaseAmount)
        assertEquals("exp", result.expenseAccountId)
        assertEquals(22L, result.feeAmount)
    }

    @Test
    fun `computeFee is null without a rate or for invalid fees`() {
        fun fee(amount: Long, instrumentBalance: Long = fromInstrumentBalance) = SwapBuilder.computeFee(
            "exp", amount, fromAmount, toAmount, fromBalance, instrumentBalance
        )
        assertNull(fee(0L))
        assertNull(fee(toAmount))
        assertNull(fee(fee, instrumentBalance = 0L))
    }

    @Test
    fun `buildEntries with a fee nets the to side and debits the expense account`() {
        val swapFee = SwapBuilder.computeFee(
            "exp", fee, fromAmount, toAmount, fromBalance, fromInstrumentBalance
        )
        val base = 22_644_000L

        val entries = SwapBuilder.buildEntries("usd", "eth", fromAmount, toAmount, base, swapFee)

        assertEquals(3, entries.size)
        assertEquals(base, entries[0].creditAmount)
        assertEquals(fromAmount, entries[0].instrumentCreditAmount)
        assertEquals(base - 22_644L, entries[1].debitAmount)
        assertEquals(toAmount - fee, entries[1].instrumentDebitAmount)
        assertEquals(fromAmount - 5_032L, entries[1].intermediaryDebitAmount)
        assertEquals("exp", entries[2].accountId)
        assertEquals(22_644L, entries[2].debitAmount)
        assertNull(entries[2].instrumentDebitAmount)
        assertTrue(TransactionValidator.validate(entries) is TransactionValidator.ValidationResult.Valid)
    }

    @Test
    fun `buildEntries without a fee is the plain two-entry swap`() {
        assertEquals(2, SwapBuilder.buildEntries("usd", "eth", 100L, 3L, 450L, null).size)
    }

    @Test
    fun `buildEntries pass the validator`() {
        val entries = SwapBuilder.buildEntries("usd", "btc", fromAmount = 100L, toAmount = 3L, baseAmount = 450L)

        assertTrue(TransactionValidator.validate(entries) is TransactionValidator.ValidationResult.Valid)
    }

    // Base-currency From: 100,000 Ar buys 3,000 units of To.
    private fun assertValid(entries: List<TransactionValidator.EntryData>) =
        assertTrue(TransactionValidator.validate(entries) is TransactionValidator.ValidationResult.Valid)

    @Test
    fun `buildBaseEntries credits from in base and debits to its instrument with no intermediary`() {
        val entries = SwapBuilder.buildBaseEntries("cash", "usd", amount = 100_000L, toAmount = 3_000L)

        assertEquals(2, entries.size)
        assertEquals("cash", entries[0].accountId)
        assertEquals(100_000L, entries[0].creditAmount)
        assertNull(entries[0].debitAmount)
        assertNull(entries[0].instrumentCreditAmount)
        assertEquals("usd", entries[1].accountId)
        assertEquals(100_000L, entries[1].debitAmount)
        assertEquals(3_000L, entries[1].instrumentDebitAmount)
        assertNull(entries[1].intermediaryDebitAmount)
        assertValid(entries)
    }

    @Test
    fun `buildBaseEntries yields null amounts for non-positive values`() {
        val entries = SwapBuilder.buildBaseEntries("cash", "usd", amount = 0L, toAmount = 0L)

        assertNull(entries[0].creditAmount)
        assertNull(entries[1].debitAmount)
        assertNull(entries[1].instrumentDebitAmount)
    }

    @Test
    fun `computeBaseSwapFee values the fee at the swap price in base units`() {
        val result = SwapBuilder.computeBaseSwapFee("exp", feeAmount = 30L, fromAmount = 100_000L, toAmount = 3_000L)!!

        // 30 * 100,000 / 3,000 = 1,000 base units, which are also the from units
        assertEquals(1_000L, result.feeBaseAmount)
        assertEquals(1_000L, result.feeFromAmount)
        assertEquals(30L, result.feeAmount)
        assertEquals("exp", result.expenseAccountId)
    }

    @Test
    fun `computeBaseSwapFee is null for invalid fees`() {
        assertNull(SwapBuilder.computeBaseSwapFee("exp", 0L, 100_000L, 3_000L))
        assertNull(SwapBuilder.computeBaseSwapFee("exp", 3_000L, 100_000L, 3_000L))
        assertNull(SwapBuilder.computeBaseSwapFee("exp", 30L, 0L, 3_000L))
    }

    @Test
    fun `buildBaseEntries with a swap fee nets the to side and debits the expense`() {
        val swapFee = SwapBuilder.computeBaseSwapFee("exp", 30L, 100_000L, 3_000L)

        val entries = SwapBuilder.buildBaseEntries("cash", "usd", 100_000L, 3_000L, swapFee)

        assertEquals(3, entries.size)
        assertEquals(100_000L, entries[0].creditAmount)
        assertEquals(99_000L, entries[1].debitAmount)
        assertEquals(2_970L, entries[1].instrumentDebitAmount)
        assertEquals("exp", entries[2].accountId)
        assertEquals(1_000L, entries[2].debitAmount)
        assertValid(entries)
    }

    @Test
    fun `buildBaseEntries with a secondary fee adds it on top of the from credit`() {
        val entries = SwapBuilder.buildBaseEntries(
            "cash", "usd", 100_000L, 3_000L, secondaryFee = SwapBuilder.SecondaryFee("exp2", 500L)
        )

        assertEquals(3, entries.size)
        assertEquals(100_500L, entries[0].creditAmount)
        assertEquals(100_000L, entries[1].debitAmount)
        assertEquals(3_000L, entries[1].instrumentDebitAmount)
        assertEquals("exp2", entries[2].accountId)
        assertEquals(500L, entries[2].debitAmount)
        assertValid(entries)
    }

    @Test
    fun `buildBaseEntries with both fees balances`() {
        val swapFee = SwapBuilder.computeBaseSwapFee("exp", 30L, 100_000L, 3_000L)

        val entries = SwapBuilder.buildBaseEntries(
            "cash", "usd", 100_000L, 3_000L, swapFee, SwapBuilder.SecondaryFee("exp2", 500L)
        )

        assertEquals(4, entries.size)
        assertEquals(100_500L, entries[0].creditAmount)
        assertEquals(99_000L, entries[1].debitAmount)
        assertEquals(1_000L, entries[2].debitAmount)
        assertEquals(500L, entries[3].debitAmount)
        assertValid(entries)
    }

    @Test
    fun `buildBaseEntries leaves the from credit null when the amount is not positive`() {
        val entries = SwapBuilder.buildBaseEntries(
            "cash", "usd", 0L, 3_000L, secondaryFee = SwapBuilder.SecondaryFee("exp2", 500L)
        )

        assertNull(entries[0].creditAmount)
    }
}
