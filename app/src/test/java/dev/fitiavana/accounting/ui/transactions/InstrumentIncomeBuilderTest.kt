package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Test

class InstrumentIncomeBuilderTest {

    private val cash = Account(id = "cash", name = "Cash", type = "asset")
    private val usdWallet = Account(
        id = "usd_wallet",
        name = "USD Wallet",
        type = "asset",
        instrumentCode = "USD"
    )
    private val btcWalletWithIntermediary = Account(
        id = "btc_wallet",
        name = "BTC Wallet",
        type = "asset",
        instrumentCode = "BTC",
        intermediaryInstrumentCode = "USD"
    )
    private val salesRevenue = Account(id = "sales_revenue", name = "Sales Revenue", type = "revenue")
    private val revenueWithInstrument = Account(
        id = "revenue_with_instrument",
        name = "Revenue With Instrument",
        type = "revenue",
        instrumentCode = "USD"
    )
    private val revenueWithIntermediary = Account(
        id = "revenue_with_intermediary",
        name = "Revenue With Intermediary",
        type = "revenue",
        intermediaryInstrumentCode = "USD"
    )
    private val loan = Account(id = "loan", name = "Loan", type = "liability")

    @Test
    fun `selectableAssetAccounts keeps only asset accounts with an instrument and no intermediary`() {
        val result = InstrumentIncomeBuilder.selectableAssetAccounts(
            listOf(cash, usdWallet, btcWalletWithIntermediary, salesRevenue, loan)
        )

        assertEquals(listOf(usdWallet), result)
    }

    @Test
    fun `selectableRevenueAccounts keeps only revenue accounts with no instrument and no intermediary`() {
        val result = InstrumentIncomeBuilder.selectableRevenueAccounts(
            listOf(salesRevenue, revenueWithInstrument, revenueWithIntermediary, loan, usdWallet)
        )

        assertEquals(listOf(salesRevenue), result)
    }

    @Test
    fun `selectableRevenueAccounts is empty when no revenue accounts exist`() {
        val result = InstrumentIncomeBuilder.selectableRevenueAccounts(listOf(cash, usdWallet, loan))

        assertEquals(emptyList<Account>(), result)
    }

    @Test
    fun `buildEntries debits the asset account and credits the revenue account in base and instrument amounts`() {
        val entries = InstrumentIncomeBuilder.buildEntries(
            assetAccountId = "usd_wallet",
            revenueAccountId = "sales_revenue",
            instrumentAmount = 150L,
            baseAmount = 60_000L
        )

        assertEquals(
            listOf(
                TransactionValidator.EntryData(
                    accountId = "usd_wallet",
                    debitAmount = 60_000L,
                    creditAmount = null,
                    instrumentDebitAmount = 150L,
                    instrumentCreditAmount = null
                ),
                TransactionValidator.EntryData(
                    accountId = "sales_revenue",
                    debitAmount = null,
                    creditAmount = 60_000L,
                    instrumentDebitAmount = null,
                    instrumentCreditAmount = null
                )
            ),
            entries
        )
    }

    @Test
    fun `buildEntries maps null or zero amounts so the entries read as incomplete`() {
        val entries = InstrumentIncomeBuilder.buildEntries(
            assetAccountId = "usd_wallet",
            revenueAccountId = "sales_revenue",
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
        val entries = InstrumentIncomeBuilder.buildEntries(
            assetAccountId = "usd_wallet",
            revenueAccountId = "sales_revenue",
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
        val entries = InstrumentIncomeBuilder.buildEntries(
            assetAccountId = "usd_wallet",
            revenueAccountId = "usd_wallet",
            instrumentAmount = 150L,
            baseAmount = 60_000L
        )

        assertEquals(
            TransactionValidator.ValidationResult.Error.DuplicateAccount,
            TransactionValidator.validate(entries)
        )
    }

    @Test
    fun `built entries pass the mixed debit credit check`() {
        val entries = InstrumentIncomeBuilder.buildEntries(
            assetAccountId = "usd_wallet",
            revenueAccountId = "sales_revenue",
            instrumentAmount = 150L,
            baseAmount = 60_000L
        )

        assertEquals(
            TransactionValidator.ValidationResult.Valid,
            TransactionValidator.validate(entries)
        )
    }
}
