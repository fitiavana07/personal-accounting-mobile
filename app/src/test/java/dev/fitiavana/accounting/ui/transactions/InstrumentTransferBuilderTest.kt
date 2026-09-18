package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Test

class InstrumentTransferBuilderTest {

    private val cash = Account(id = "cash", name = "Cash", type = "asset")
    private val usdWallet = Account(
        id = "usd_wallet",
        name = "USD Wallet",
        type = "asset",
        instrumentCode = "USD"
    )
    private val usdSavings = Account(
        id = "usd_savings",
        name = "USD Savings",
        type = "asset",
        instrumentCode = "USD"
    )
    private val eurWallet = Account(
        id = "eur_wallet",
        name = "EUR Wallet",
        type = "asset",
        instrumentCode = "EUR"
    )
    private val btcWalletWithIntermediary = Account(
        id = "btc_wallet",
        name = "BTC Wallet",
        type = "asset",
        instrumentCode = "BTC",
        intermediaryInstrumentCode = "USD"
    )
    private val revenue = Account(id = "rev", name = "Revenue", type = "revenue")
    private val loan = Account(id = "loan", name = "Loan", type = "liability")

    @Test
    fun `selectableFromAccounts keeps only asset accounts with an instrument and no intermediary`() {
        val result = InstrumentTransferBuilder.selectableFromAccounts(
            listOf(cash, usdWallet, eurWallet, btcWalletWithIntermediary, revenue, loan)
        )

        assertEquals(listOf(usdWallet, eurWallet), result)
    }

    @Test
    fun `selectableToAccounts is empty when no from account is selected`() {
        val result = InstrumentTransferBuilder.selectableToAccounts(
            listOf(usdWallet, usdSavings, eurWallet),
            fromAccount = null
        )

        assertEquals(emptyList<Account>(), result)
    }

    @Test
    fun `selectableToAccounts keeps only accounts matching the from account's instrument`() {
        val result = InstrumentTransferBuilder.selectableToAccounts(
            listOf(usdWallet, usdSavings, eurWallet, cash),
            fromAccount = usdWallet
        )

        assertEquals(listOf(usdWallet, usdSavings), result)
    }

    @Test
    fun `buildEntries credits the from account and debits the to account in both base and instrument amounts`() {
        val entries = InstrumentTransferBuilder.buildEntries(
            fromAccountId = "usd_wallet",
            toAccountId = "usd_savings",
            instrumentAmount = 150L,
            baseAmount = 60_000L
        )

        assertEquals(
            listOf(
                TransactionValidator.EntryData(
                    accountId = "usd_wallet",
                    debitAmount = null,
                    creditAmount = 60_000L,
                    instrumentDebitAmount = null,
                    instrumentCreditAmount = 150L
                ),
                TransactionValidator.EntryData(
                    accountId = "usd_savings",
                    debitAmount = 60_000L,
                    creditAmount = null,
                    instrumentDebitAmount = 150L,
                    instrumentCreditAmount = null
                )
            ),
            entries
        )
    }

    @Test
    fun `buildEntries maps null or zero amounts so the entries read as incomplete`() {
        val entries = InstrumentTransferBuilder.buildEntries(
            fromAccountId = "usd_wallet",
            toAccountId = "usd_savings",
            instrumentAmount = 0L,
            baseAmount = null
        )

        assertEquals(
            TransactionValidator.ValidationResult.Error.Incomplete,
            TransactionValidator.validate(entries)
        )
    }

    @Test
    fun `built entries are balanced and valid`() {
        val entries = InstrumentTransferBuilder.buildEntries(
            fromAccountId = "usd_wallet",
            toAccountId = "usd_savings",
            instrumentAmount = 150L,
            baseAmount = 60_000L
        )

        assertEquals(60_000L to 60_000L, TransactionValidator.totals(entries))
        assertEquals(
            TransactionValidator.ValidationResult.Valid,
            TransactionValidator.validate(entries)
        )
    }

    @Test
    fun `built entries are rejected when the same account is used twice`() {
        val entries = InstrumentTransferBuilder.buildEntries(
            fromAccountId = "usd_wallet",
            toAccountId = "usd_wallet",
            instrumentAmount = 150L,
            baseAmount = 60_000L
        )

        assertEquals(
            TransactionValidator.ValidationResult.Error.DuplicateAccount,
            TransactionValidator.validate(entries)
        )
    }
}
