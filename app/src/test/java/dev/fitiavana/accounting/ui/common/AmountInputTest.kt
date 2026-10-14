package dev.fitiavana.accounting.ui.common

import android.text.InputType
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AmountInputTest {

    // --- AmountInput.format -------------------------------------------------

    @Test
    fun format_groupsThousandsWithComma() {
        assertEquals("1,234,567", AmountInput.format("1234567", false))
    }

    @Test
    fun format_leavesShortNumbersAlone() {
        assertEquals("999", AmountInput.format("999", false))
        assertEquals("", AmountInput.format("", false))
    }

    @Test
    fun format_isIdempotentOnAlreadyFormattedText() {
        assertEquals("12,345", AmountInput.format("12,345", false))
    }

    @Test
    fun format_dropsNonDigitsForIntegerFields() {
        assertEquals("1,234", AmountInput.format("1a2.3,4", false))
    }

    @Test
    fun format_keepsDecimalPartUngrouped() {
        assertEquals("1,234.56789", AmountInput.format("1234.56789", true))
    }

    @Test
    fun format_keepsTrailingDecimalPoint() {
        assertEquals("1,234.", AmountInput.format("1234.", true))
    }

    @Test
    fun format_keepsOnlyFirstDecimalPoint() {
        assertEquals("1.23", AmountInput.format("1.2.3", true))
    }

    @Test
    fun format_keepsLeadingDecimalPoint() {
        assertEquals("0.5", AmountInput.format("0.5", true))
        assertEquals(".5", AmountInput.format(".5", true))
    }

    // --- AmountInput.plain --------------------------------------------------

    @Test
    fun plain_stripsSeparators() {
        assertEquals("1234567", AmountInput.plain("1,234,567"))
    }

    @Test
    fun plain_keepsDecimalPoint() {
        assertEquals("1234.5", AmountInput.plain("1,234.5"))
    }

    // --- EditText integration ----------------------------------------------

    private fun editText(allowDecimal: Boolean): EditText {
        val edit = EditText(ApplicationProvider.getApplicationContext())
        edit.inputType = if (allowDecimal) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        } else {
            InputType.TYPE_CLASS_NUMBER
        }
        edit.enableAmountFormatting(allowDecimal)
        return edit
    }

    @Test
    fun editText_formatsAsTheUserTypes() {
        val edit = editText(false)
        edit.setText("1234567")
        assertEquals("1,234,567", edit.text.toString())
        assertEquals("1234567", edit.plainText())
    }

    @Test
    fun editText_appendingDigitKeepsCursorAtEnd() {
        val edit = editText(false)
        edit.setText("123")
        edit.setSelection(edit.length())
        edit.append("4")
        assertEquals("1,234", edit.text.toString())
        assertEquals(5, edit.selectionStart)
    }

    @Test
    fun editText_insertingInTheMiddleKeepsCursorAfterInsertedDigit() {
        val edit = editText(false)
        edit.setText("1,234")
        edit.setSelection(1) // after the leading "1"
        edit.text.insert(1, "9")
        assertEquals("19,234", edit.text.toString())
        assertEquals(2, edit.selectionStart)
    }

    @Test
    fun editText_deletingDigitRegroups() {
        val edit = editText(false)
        edit.setText("1,234")
        edit.text.delete(0, 1)
        assertEquals("234", edit.text.toString())
    }

    @Test
    fun editText_decimalFieldKeepsFractionAndFormatsInteger() {
        val edit = editText(true)
        edit.setText("1234.5")
        assertEquals("1,234.5", edit.text.toString())
        assertEquals("1234.5", edit.plainText())
    }

    @Test
    fun editText_emptyStaysEmpty() {
        val edit = editText(false)
        edit.setText("12")
        edit.setText("")
        assertEquals("", edit.text.toString())
        assertEquals("", edit.plainText())
    }

    @Test
    fun editText_keepsNumericKeyboard() {
        val edit = editText(true)
        assertEquals(InputType.TYPE_CLASS_NUMBER, edit.inputType and InputType.TYPE_MASK_CLASS)
    }
}
