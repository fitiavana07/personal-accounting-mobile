package dev.fitiavana.accounting.ui.common

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

/**
 * Live thousands separators ("," — English style only) for amount fields. The displayed text is
 * grouped; [plain] / [plainText] give back the bare number.
 */
object AmountInput {
    private const val SEPARATOR = ','
    private const val DECIMAL_POINT = '.'

    /** Groups the integer part of [text]; non-digits are dropped, and a single "." is kept when [allowDecimal]. */
    fun format(text: String, allowDecimal: Boolean): String {
        val plain = plain(text)
        val point = if (allowDecimal) plain.indexOf(DECIMAL_POINT) else -1
        val integer = (if (point >= 0) plain.substring(0, point) else plain).filter { it.isDigit() }
        val fraction = if (point >= 0) {
            DECIMAL_POINT + plain.substring(point + 1).filter { it.isDigit() }
        } else {
            ""
        }
        val grouped = StringBuilder()
        integer.forEachIndexed { index, digit ->
            if (index > 0 && (integer.length - index) % 3 == 0) grouped.append(SEPARATOR)
            grouped.append(digit)
        }
        return grouped.toString() + fraction
    }

    /** [text] without the grouping separators: digits and "." only. */
    fun plain(text: CharSequence): String = text.filter { it.isDigit() || it == DECIMAL_POINT }.toString()

    private fun isSignificant(c: Char) = c.isDigit() || c == DECIMAL_POINT

    /** Number of characters in [text] before [cursor] that are part of the number (digits and "."). */
    private fun significantBefore(text: CharSequence, cursor: Int): Int =
        (0 until cursor.coerceIn(0, text.length)).count { isSignificant(text[it]) }

    /** Index in [text] just after its [count]-th digit or ".". */
    private fun positionAfter(text: CharSequence, count: Int): Int {
        if (count == 0) return 0
        var seen = 0
        for (i in text.indices) {
            if (isSignificant(text[i])) seen++
            if (seen == count) return i + 1
        }
        return text.length
    }

    internal class Watcher(
        private val edit: EditText,
        private val allowDecimal: () -> Boolean
    ) : TextWatcher {
        private var updating = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
            if (updating) return
            val formatted = format(s.toString(), allowDecimal())
            if (formatted == s.toString()) return
            val significant = significantBefore(s, edit.selectionStart)
            updating = true
            // The number keyboard's digits-only filter would strip the separators we insert.
            val filters = s.filters
            s.filters = emptyArray()
            try {
                s.replace(0, s.length, formatted)
                edit.setSelection(positionAfter(formatted, significant))
            } finally {
                s.filters = filters
                updating = false
            }
        }
    }
}

/**
 * Makes this field show thousands separators as the user types. [allowDecimal] keeps a "." fraction
 * (instrument quantities); otherwise only whole numbers. Read the value with [plainText].
 */
fun EditText.enableAmountFormatting(allowDecimal: Boolean = false) {
    enableAmountFormatting { allowDecimal }
}

/** Like [enableAmountFormatting] but [allowDecimal] is asked at every edit, for fields whose unit changes. */
fun EditText.enableAmountFormatting(allowDecimal: () -> Boolean) {
    addTextChangedListener(AmountInput.Watcher(this, allowDecimal))
}

/** The field's number without thousands separators, e.g. "1234567" (or "1234.5"). Blank if empty. */
fun EditText.plainText(): String = AmountInput.plain(text.toString().trim())
