package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Test

class SimpleTransferBuilderTest {

    private val cash = Account(id = "cash", name = "Cash", type = "asset")
    private val bank = Account(id = "bank", name = "Bank", type = "asset")
    private val wallet = Account(
        id = "wallet",
        name = "USD Wallet",
        type = "asset",
        instrumentCode = "USD"
    )
    private val revenue =
        Account(id = "rev", name = "Revenue", type = "revenue")
    private val loan = Account(id = "loan", name = "Loan", type = "liability")

    @Test
    fun `selectableAccounts keeps only asset accounts without an instrument`() {
        val result = SimpleTransferBuilder.selectableAccounts(
            listOf(cash, wallet, revenue, loan, bank)
        )

        assertEquals(listOf(cash, bank), result)
    }

    @Test
    fun `selectableAccounts returns empty list when no account qualifies`() {
        assertEquals(
            emptyList<Account>(),
            SimpleTransferBuilder.selectableAccounts(listOf(wallet, revenue))
        )
    }

    @Test
    fun `buildEntries credits the from account and debits the to account`() {
        val entries =
            SimpleTransferBuilder.buildEntries("cash", "bank", 25_000L)

        assertEquals(
            listOf(
                TransactionValidator.EntryData(
                    accountId = "cash",
                    debitAmount = null,
                    creditAmount = 25_000L
                ),
                TransactionValidator.EntryData(
                    accountId = "bank",
                    debitAmount = 25_000L,
                    creditAmount = null
                )
            ),
            entries
        )
    }

    @Test
    fun `buildEntries leaves no instrument amounts on either entry`() {
        val entries =
            SimpleTransferBuilder.buildEntries("cash", "bank", 25_000L)

        entries.forEach { entry ->
            assertEquals(null, entry.instrumentDebitAmount)
            assertEquals(null, entry.instrumentCreditAmount)
            assertEquals(null, entry.intermediaryDebitAmount)
            assertEquals(null, entry.intermediaryCreditAmount)
        }
    }

    @Test
    fun `buildEntries maps a zero amount to null amounts so the entries read as incomplete`() {
        val entries = SimpleTransferBuilder.buildEntries("cash", "bank", 0L)

        assertEquals(2, entries.size)
        entries.forEach { entry ->
            assertEquals(null, entry.debitAmount)
            assertEquals(null, entry.creditAmount)
        }
        assertEquals(
            TransactionValidator.ValidationResult.Error.Incomplete,
            TransactionValidator.validate(entries)
        )
    }

    @Test
    fun `built entries are balanced`() {
        val entries =
            SimpleTransferBuilder.buildEntries("cash", "bank", 25_000L)

        assertEquals(25_000L to 25_000L, TransactionValidator.totals(entries))
        assertEquals(
            TransactionValidator.ValidationResult.Valid,
            TransactionValidator.validate(entries)
        )
    }

    @Test
    fun `built entries are rejected when the same account is used twice`() {
        val entries =
            SimpleTransferBuilder.buildEntries("cash", "cash", 25_000L)

        assertEquals(
            TransactionValidator.ValidationResult.Error.DuplicateAccount,
            TransactionValidator.validate(entries)
        )
    }
}
