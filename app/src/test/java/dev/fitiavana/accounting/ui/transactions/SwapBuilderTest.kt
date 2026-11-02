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
    fun `selectableFeeAccounts offers only expense accounts`() {
        val fuel = Account(id = "fuel", name = "Fees", type = "expense")
        val rent = Account(id = "rent", name = "Rent", type = "expense")

        assertEquals(listOf(fuel, rent), SwapBuilder.selectableFeeAccounts(all + fuel + rent))
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
}
