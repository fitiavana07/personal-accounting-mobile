package dev.fitiavana.accounting

import dev.fitiavana.accounting.ui.transactions.TransactionDisplay
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionDisplayTest {

    // --- formatAccountList ---

    @Test
    fun `empty list returns question mark`() {
        assertEquals("?", TransactionDisplay.formatAccountList(emptyList()))
    }

    @Test
    fun `single account returns name`() {
        assertEquals("Cash", TransactionDisplay.formatAccountList(listOf("Cash")))
    }

    @Test
    fun `two accounts joined with comma`() {
        assertEquals("Cash, Bank", TransactionDisplay.formatAccountList(listOf("Cash", "Bank")))
    }

    @Test
    fun `three accounts truncates with ellipsis`() {
        assertEquals("Cash, Bank, ...", TransactionDisplay.formatAccountList(listOf("Cash", "Bank", "Savings")))
    }

    @Test
    fun `four accounts shows only first two then ellipsis`() {
        assertEquals("A, B, ...", TransactionDisplay.formatAccountList(listOf("A", "B", "C", "D")))
    }

    // --- formatNotePreview ---

    @Test
    fun `blank note returns empty string`() {
        assertEquals("", TransactionDisplay.formatNotePreview(""))
        assertEquals("", TransactionDisplay.formatNotePreview("   "))
    }

    @Test
    fun `single short line returned as-is`() {
        assertEquals("Hello", TransactionDisplay.formatNotePreview("Hello"))
    }

    @Test
    fun `single line over 60 chars truncated with ellipsis`() {
        val long = "A".repeat(65)
        val result = TransactionDisplay.formatNotePreview(long)
        assertEquals("A".repeat(60) + "...", result)
    }

    @Test
    fun `multiline note shows first line plus ellipsis`() {
        val note = "First line\nSecond line"
        assertEquals("First line...", TransactionDisplay.formatNotePreview(note))
    }

    @Test
    fun `multiline note with long first line truncates at 60`() {
        val firstLine = "B".repeat(70)
        val note = "$firstLine\nSecond line"
        assertEquals("B".repeat(60) + "...", TransactionDisplay.formatNotePreview(note))
    }

    @Test
    fun `single line exactly 60 chars not truncated`() {
        val exact = "C".repeat(60)
        assertEquals(exact, TransactionDisplay.formatNotePreview(exact))
    }

    // --- formatAmount ---

    @Test
    fun `small amount formatted without grouping`() {
        assertEquals("999", TransactionDisplay.formatAmount(999))
    }

    @Test
    fun `thousands formatted with comma`() {
        assertEquals("1,000", TransactionDisplay.formatAmount(1000))
        assertEquals("1,234", TransactionDisplay.formatAmount(1234))
    }

    @Test
    fun `millions formatted with commas`() {
        assertEquals("1,000,000", TransactionDisplay.formatAmount(1_000_000))
    }

    @Test
    fun `zero formatted as zero`() {
        assertEquals("0", TransactionDisplay.formatAmount(0))
    }
}
