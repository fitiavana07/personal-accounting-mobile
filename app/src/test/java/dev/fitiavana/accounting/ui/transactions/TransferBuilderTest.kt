package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferBuilderTest {

    private val cash = Account(id = "cash", name = "Cash", type = "asset")
    private val bank = Account(id = "bank", name = "Bank", type = "asset")
    private val usdWallet = Account(id = "usd", name = "USD Wallet", type = "asset", instrumentCode = "USD")
    private val usdSavings = Account(id = "usd2", name = "USD Savings", type = "asset", instrumentCode = "USD")
    private val eurWallet = Account(id = "eur", name = "EUR Wallet", type = "asset", instrumentCode = "EUR")
    private val btcViaUsd = Account(
        id = "btc", name = "BTC", type = "asset", instrumentCode = "BTC", intermediaryInstrumentCode = "USD"
    )
    private val btcViaEur = btcViaUsd.copy(id = "btc_eur", intermediaryInstrumentCode = "EUR")
    private val revenue = Account(id = "rev", name = "Revenue", type = "revenue")

    private val all = listOf(cash, bank, usdWallet, usdSavings, eurWallet, btcViaUsd, btcViaEur, revenue)
    private val funded = all.associate { it.id to 1L }

    @Test
    fun `selectableFromAccounts offers instrument-free and instrument assets`() {
        assertEquals(
            listOf(cash, bank, usdWallet, usdSavings, eurWallet, btcViaUsd, btcViaEur),
            TransferBuilder.selectableFromAccounts(all, funded)
        )
    }

    @Test
    fun `selectableFromAccounts hides zero or unknown balances`() {
        val balances = mapOf("cash" to 0L, "usd" to 5L)

        assertEquals(listOf(usdWallet), TransferBuilder.selectableFromAccounts(all, balances))
    }

    @Test
    fun `selectableToAccounts for an instrument-free from is the other instrument-free assets`() {
        assertEquals(listOf(bank), TransferBuilder.selectableToAccounts(all, cash))
    }

    @Test
    fun `selectableToAccounts for an instrument from matches instrument and intermediary`() {
        assertEquals(listOf(usdSavings), TransferBuilder.selectableToAccounts(all, usdWallet))
        assertEquals(emptyList<Account>(), TransferBuilder.selectableToAccounts(all, btcViaUsd))
    }

    @Test
    fun `selectableToAccounts is empty without a from account`() {
        assertEquals(emptyList<Account>(), TransferBuilder.selectableToAccounts(all, null))
    }

    @Test
    fun `buildBaseEntries credits from and debits to in base only`() {
        val entries = TransferBuilder.buildBaseEntries("cash", "bank", 500L)

        assertEquals(500L, entries[0].creditAmount)
        assertNull(entries[0].debitAmount)
        assertNull(entries[0].instrumentCreditAmount)
        assertEquals(500L, entries[1].debitAmount)
        assertEquals("bank", entries[1].accountId)
    }

    @Test
    fun `selectableFromAccounts keeps accounts with a negative balance`() {
        assertEquals(listOf(cash), TransferBuilder.selectableFromAccounts(listOf(cash, bank), mapOf("cash" to -5L)))
    }

    @Test
    fun `build entries map zero or null amounts to null so they read as incomplete`() {
        val base = TransferBuilder.buildBaseEntries("cash", "bank", 0L)
        val instrument = TransferBuilder.buildInstrumentEntries("usd", "usd2", 0L, null, 0L)

        assertEquals(TransactionValidator.ValidationResult.Error.Incomplete, TransactionValidator.validate(base))
        assertEquals(TransactionValidator.ValidationResult.Error.Incomplete, TransactionValidator.validate(instrument))
        assertNull(instrument[0].intermediaryCreditAmount)
    }

    @Test
    fun `built entries are balanced and valid, and reject the same account twice`() {
        val base = TransferBuilder.buildBaseEntries("cash", "bank", 500L)
        val instrument = TransferBuilder.buildInstrumentEntries("usd", "usd2", 10L, 200L)

        assertEquals(TransactionValidator.ValidationResult.Valid, TransactionValidator.validate(base))
        assertEquals(TransactionValidator.ValidationResult.Valid, TransactionValidator.validate(instrument))
        assertEquals(
            TransactionValidator.ValidationResult.Error.DuplicateAccount,
            TransactionValidator.validate(TransferBuilder.buildBaseEntries("cash", "cash", 500L))
        )
    }

    @Test
    fun `computeBaseFee takes the fee as the base value`() {
        assertEquals(
            TransferBuilder.TransferFee("fees", 20L, 20L, null),
            TransferBuilder.computeBaseFee("fees", feeAmount = 20L, amount = 500L)
        )
    }

    @Test
    fun `computeBaseFee is null unless the fee is between zero and the amount`() {
        assertNull(TransferBuilder.computeBaseFee("fees", 0L, 500L))
        assertNull(TransferBuilder.computeBaseFee("fees", 500L, 500L))
        assertNull(TransferBuilder.computeBaseFee("fees", 600L, 500L))
    }

    @Test
    fun `computeInstrumentFee values the fee at the from rate`() {
        // 100 USD worth 450,000 Ar: 2 USD = 9,000 Ar.
        assertEquals(
            TransferBuilder.TransferFee("fees", 2L, 9_000L, null),
            TransferBuilder.computeInstrumentFee("fees", 2L, 10L, 450_000L, 100L, 0L, false)
        )
    }

    @Test
    fun `computeInstrumentFee scales the intermediary when from has one`() {
        // 100 BTC units: 450,000 Ar, 60 intermediary. Fee 5 -> 22,500 Ar, 3 intermediary.
        assertEquals(
            TransferBuilder.TransferFee("fees", 5L, 22_500L, 3L),
            TransferBuilder.computeInstrumentFee("fees", 5L, 50L, 450_000L, 100L, 60L, true)
        )
    }

    @Test
    fun `computeInstrumentFee is null without a rate or outside the amount`() {
        assertNull(TransferBuilder.computeInstrumentFee("fees", 2L, 10L, 0L, 0L, 0L, false))
        assertNull(TransferBuilder.computeInstrumentFee("fees", 0L, 10L, 450_000L, 100L, 0L, false))
        assertNull(TransferBuilder.computeInstrumentFee("fees", 10L, 10L, 450_000L, 100L, 0L, false))
    }

    @Test
    fun `buildBaseEntries with a fee debits to net and the expense the fee`() {
        val fee = TransferBuilder.TransferFee("fees", 20L, 20L, null)

        val entries = TransferBuilder.buildBaseEntries("cash", "bank", 500L, fee)

        assertEquals(3, entries.size)
        assertEquals(500L, entries[0].creditAmount)
        assertEquals(480L, entries[1].debitAmount)
        assertEquals("fees", entries[2].accountId)
        assertEquals(20L, entries[2].debitAmount)
        assertEquals(TransactionValidator.ValidationResult.Valid, TransactionValidator.validate(entries))
    }

    @Test
    fun `buildInstrumentEntries with a fee debits to net of fee and expenses the base value`() {
        val fee = TransferBuilder.TransferFee("fees", 5L, 22_500L, 3L)

        val entries = TransferBuilder.buildInstrumentEntries("btc", "btc2", 50L, 225_000L, 30L, fee)

        assertEquals(225_000L, entries[0].creditAmount)
        assertEquals(50L, entries[0].instrumentCreditAmount)
        assertEquals(30L, entries[0].intermediaryCreditAmount)
        assertEquals(202_500L, entries[1].debitAmount)
        assertEquals(45L, entries[1].instrumentDebitAmount)
        assertEquals(27L, entries[1].intermediaryDebitAmount)
        assertEquals("fees", entries[2].accountId)
        assertEquals(22_500L, entries[2].debitAmount)
        assertNull(entries[2].instrumentDebitAmount)
        assertEquals(TransactionValidator.ValidationResult.Valid, TransactionValidator.validate(entries))
    }

    @Test
    fun `buildInstrumentEntries carries instrument and intermediary amounts`() {
        val entries = TransferBuilder.buildInstrumentEntries("btc", "btc2", 10L, 200L, 30L)

        assertEquals(200L, entries[0].creditAmount)
        assertEquals(10L, entries[0].instrumentCreditAmount)
        assertEquals(30L, entries[0].intermediaryCreditAmount)
        assertEquals(10L, entries[1].instrumentDebitAmount)
        assertEquals(30L, entries[1].intermediaryDebitAmount)
    }
}
