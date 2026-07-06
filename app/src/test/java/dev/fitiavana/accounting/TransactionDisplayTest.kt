package dev.fitiavana.accounting

import dev.fitiavana.accounting.data.model.Instrument
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

    // --- formatInstrumentAmount ---

    @Test
    fun `two decimal places strips trailing zeros`() {
        val usd = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("1.5 USD", TransactionDisplay.formatInstrumentAmount(150, usd))
    }

    @Test
    fun `two decimal places with whole number strips trailing zeros then adds zero after dot`() {
        val usd = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("1.0 USD", TransactionDisplay.formatInstrumentAmount(100, usd))
    }

    @Test
    fun `two decimal places with exact cents`() {
        val usd = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("12.34 USD", TransactionDisplay.formatInstrumentAmount(1234, usd))
    }

    @Test
    fun `zero decimal places formats as integer with instrument code`() {
        val jpy = Instrument(code = "JPY", note = "", type = "currency", decimalPlaces = 0)
        assertEquals("1,500 JPY", TransactionDisplay.formatInstrumentAmount(1500, jpy))
    }

    @Test
    fun `zero decimal places small amount no grouping`() {
        val jpy = Instrument(code = "JPY", note = "", type = "currency", decimalPlaces = 0)
        assertEquals("42 JPY", TransactionDisplay.formatInstrumentAmount(42, jpy))
    }

    @Test
    fun `three decimal places strips trailing zeros`() {
        val bhd = Instrument(code = "BHD", note = "", type = "currency", decimalPlaces = 3)
        assertEquals("1.5 BHD", TransactionDisplay.formatInstrumentAmount(1500, bhd))
    }

    @Test
    fun `large amount with decimal places has grouped integer part`() {
        val usd = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("1,000.0 USD", TransactionDisplay.formatInstrumentAmount(100000, usd))
    }

    @Test
    fun `zero amount with decimal places`() {
        val usd = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("0.0 USD", TransactionDisplay.formatInstrumentAmount(0, usd))
    }

    @Test
    fun `zero amount with no decimal places`() {
        val jpy = Instrument(code = "JPY", note = "", type = "currency", decimalPlaces = 0)
        assertEquals("0 JPY", TransactionDisplay.formatInstrumentAmount(0, jpy))
    }

    // --- formatExchangeRate ---

    @Test
    fun `exchange rate with zero decimal places instrument`() {
        val jpy = Instrument(code = "JPY", note = "", type = "currency", decimalPlaces = 0)
        assertEquals("1 JPY = Ar 30", TransactionDisplay.formatExchangeRate(3000, 100, jpy))
    }

    @Test
    fun `exchange rate accounts for instrument decimal places`() {
        val usd = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
        // instrumentBalance 250 means 2.50 USD; balance 10000 Ar -> rate 4000
        assertEquals("1 USD = Ar 4,000", TransactionDisplay.formatExchangeRate(10000, 250, usd))
    }

    @Test
    fun `exchange rate rounds to nearest integer`() {
        val usd = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
        // 1000 Ar / 3.33 USD = 300.3003... -> rounds to 300
        assertEquals("1 USD = Ar 300", TransactionDisplay.formatExchangeRate(1000, 333, usd))
    }

    @Test
    fun `exchange rate returns null when instrument amount is zero`() {
        val usd = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
        assertEquals(null, TransactionDisplay.formatExchangeRate(1000, 0, usd))
    }

    @Test
    fun `exchange rate formats large values with grouping`() {
        val jpy = Instrument(code = "JPY", note = "", type = "currency", decimalPlaces = 0)
        assertEquals("1 JPY = Ar 1,000,000", TransactionDisplay.formatExchangeRate(1_000_000, 1, jpy))
    }

    @Test
    fun `exchange rate handles negative balance`() {
        val usd = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
        assertEquals("1 USD = Ar -400", TransactionDisplay.formatExchangeRate(-4000, 1000, usd))
    }
}
