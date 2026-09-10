package dev.fitiavana.accounting.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionEntryOrderTest {

    private fun debit(accountId: String, amount: Long = 100L) =
        TransactionValidator.EntryData(
            accountId = accountId,
            debitAmount = amount,
            creditAmount = null
        )

    private fun credit(accountId: String, amount: Long = 100L) =
        TransactionValidator.EntryData(
            accountId = accountId,
            debitAmount = null,
            creditAmount = amount
        )

    @Test
    fun `moves a debit entry ahead of a credit entry`() {
        val entries = listOf(credit("cash"), debit("bank"))

        assertEquals(
            listOf(debit("bank"), credit("cash")),
            TransactionEntryOrder.debitsFirst(entries)
        )
    }

    @Test
    fun `leaves entries already in debits-first order untouched`() {
        val entries = listOf(debit("bank"), credit("cash"))

        assertEquals(entries, TransactionEntryOrder.debitsFirst(entries))
    }

    @Test
    fun `keeps the relative order within each group`() {
        val entries = listOf(
            credit("cash", 100L),
            debit("bank", 300L),
            credit("wallet", 200L),
            debit("safe", 400L)
        )

        assertEquals(
            listOf(
                debit("bank", 300L),
                debit("safe", 400L),
                credit("cash", 100L),
                credit("wallet", 200L)
            ),
            TransactionEntryOrder.debitsFirst(entries)
        )
    }

    @Test
    fun `leaves an all-debit list unchanged`() {
        val entries = listOf(debit("bank"), debit("safe"))

        assertEquals(entries, TransactionEntryOrder.debitsFirst(entries))
    }

    @Test
    fun `leaves an all-credit list unchanged`() {
        val entries = listOf(credit("cash"), credit("wallet"))

        assertEquals(entries, TransactionEntryOrder.debitsFirst(entries))
    }

    @Test
    fun `returns an empty list unchanged`() {
        assertEquals(
            emptyList<TransactionValidator.EntryData>(),
            TransactionEntryOrder.debitsFirst(emptyList())
        )
    }

    @Test
    fun `treats an entry with no amount as a non-debit`() {
        val empty = TransactionValidator.EntryData(
            accountId = "empty",
            debitAmount = null,
            creditAmount = null
        )
        val entries = listOf(empty, debit("bank"))

        assertEquals(
            listOf(debit("bank"), empty),
            TransactionEntryOrder.debitsFirst(entries)
        )
    }

    @Test
    fun `carries instrument amounts along with the reordered entry`() {
        val instrumentDebit = TransactionValidator.EntryData(
            accountId = "wallet",
            debitAmount = 100L,
            creditAmount = null,
            instrumentDebitAmount = 25L,
            intermediaryDebitAmount = 7L
        )
        val entries = listOf(credit("cash"), instrumentDebit)

        assertEquals(
            listOf(instrumentDebit, credit("cash")),
            TransactionEntryOrder.debitsFirst(entries)
        )
    }

    @Test
    fun `reordering does not change the validation outcome`() {
        val entries = listOf(credit("cash"), debit("bank"))

        assertEquals(
            TransactionValidator.validate(entries),
            TransactionValidator.validate(
                TransactionEntryOrder.debitsFirst(entries)
            )
        )
    }
}
