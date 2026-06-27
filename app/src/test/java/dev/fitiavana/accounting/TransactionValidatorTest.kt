package dev.fitiavana.accounting

import dev.fitiavana.accounting.ui.transactions.TransactionValidator
import dev.fitiavana.accounting.ui.transactions.TransactionValidator.EntryData
import dev.fitiavana.accounting.ui.transactions.TransactionValidator.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionValidatorTest {

    // --- Valid cases ---

    @Test
    fun `valid two-entry balanced transaction returns Valid`() {
        val entries = listOf(
            EntryData("acc1", debitAmount = 100, creditAmount = null),
            EntryData("acc2", debitAmount = null, creditAmount = 100)
        )
        assertEquals(ValidationResult.Valid, TransactionValidator.validate(entries))
    }

    @Test
    fun `valid multi-entry balanced transaction returns Valid`() {
        val entries = listOf(
            EntryData("acc1", debitAmount = 300, creditAmount = null),
            EntryData("acc2", debitAmount = null, creditAmount = 200),
            EntryData("acc3", debitAmount = null, creditAmount = 100)
        )
        assertEquals(ValidationResult.Valid, TransactionValidator.validate(entries))
    }

    // --- Duplicate account ---

    @Test
    fun `duplicate account ids returns DuplicateAccount`() {
        val entries = listOf(
            EntryData("acc1", debitAmount = 100, creditAmount = null),
            EntryData("acc1", debitAmount = null, creditAmount = 100)
        )
        assertEquals(ValidationResult.Error.DuplicateAccount, TransactionValidator.validate(entries))
    }

    @Test
    fun `duplicate account across three entries returns DuplicateAccount`() {
        val entries = listOf(
            EntryData("acc1", debitAmount = 100, creditAmount = null),
            EntryData("acc2", debitAmount = null, creditAmount = 50),
            EntryData("acc1", debitAmount = null, creditAmount = 50)
        )
        assertEquals(ValidationResult.Error.DuplicateAccount, TransactionValidator.validate(entries))
    }

    // --- Both filled ---

    @Test
    fun `entry with both debit and credit filled returns BothFilled`() {
        val entries = listOf(
            EntryData("acc1", debitAmount = 100, creditAmount = 100),
            EntryData("acc2", debitAmount = null, creditAmount = 100)
        )
        assertEquals(ValidationResult.Error.BothFilled, TransactionValidator.validate(entries))
    }

    // --- Incomplete ---

    @Test
    fun `entry with neither debit nor credit returns Incomplete`() {
        val entries = listOf(
            EntryData("acc1", debitAmount = null, creditAmount = null),
            EntryData("acc2", debitAmount = null, creditAmount = 100)
        )
        assertEquals(ValidationResult.Error.Incomplete, TransactionValidator.validate(entries))
    }

    // --- Unbalanced ---

    @Test
    fun `unbalanced debits and credits returns Unbalanced`() {
        val entries = listOf(
            EntryData("acc1", debitAmount = 100, creditAmount = null),
            EntryData("acc2", debitAmount = null, creditAmount = 90)
        )
        assertEquals(ValidationResult.Error.Unbalanced, TransactionValidator.validate(entries))
    }

    @Test
    fun `zero total debit with zero total credit returns Valid`() {
        val entries = listOf(
            EntryData("acc1", debitAmount = 0, creditAmount = null),
            EntryData("acc2", debitAmount = null, creditAmount = 0)
        )
        assertEquals(ValidationResult.Valid, TransactionValidator.validate(entries))
    }

    // --- Single entry ---

    @Test
    fun `single entry with only debit is Unbalanced`() {
        val entries = listOf(EntryData("acc1", debitAmount = 100, creditAmount = null))
        assertEquals(ValidationResult.Error.Unbalanced, TransactionValidator.validate(entries))
    }

    @Test
    fun `single entry with only credit is Unbalanced`() {
        val entries = listOf(EntryData("acc1", debitAmount = null, creditAmount = 100))
        assertEquals(ValidationResult.Error.Unbalanced, TransactionValidator.validate(entries))
    }

    // --- Empty list ---

    @Test
    fun `empty list returns Valid because totals are both zero`() {
        assertEquals(ValidationResult.Valid, TransactionValidator.validate(emptyList()))
    }

    // --- Error priority: duplicate is checked before balance ---

    @Test
    fun `duplicate account is caught before balance check`() {
        val entries = listOf(
            EntryData("acc1", debitAmount = 100, creditAmount = null),
            EntryData("acc1", debitAmount = null, creditAmount = 200)
        )
        assertEquals(ValidationResult.Error.DuplicateAccount, TransactionValidator.validate(entries))
    }
}
