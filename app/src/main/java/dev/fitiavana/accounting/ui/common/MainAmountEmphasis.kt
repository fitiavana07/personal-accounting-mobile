package dev.fitiavana.accounting.ui.common

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import dev.fitiavana.accounting.R

/** A base-currency amount as "1,000 Ar" with a non-breaking space, matching the "…&#160;Ar" balance strings. */
fun baseAmountNoWrap(amount: Long): String = "${TransactionDisplay.formatAmount(amount)} Ar"

/**
 * [line] with [mainAmount] (the amount in the account's main instrument: its own instrument, or Ar
 * for an account without one) shown bold in the app's gold. [mainAmount] must be spelled exactly as in
 * [line]; if it isn't found the line is returned unstyled. A [negative] amount is shown in the loss red
 * instead of the gold.
 */
fun emphasizeMainAmount(
    context: Context,
    line: String,
    mainAmount: String,
    negative: Boolean = false
): CharSequence {
    val spannable = SpannableString(line)
    val start = line.indexOf(mainAmount)
    if (start < 0) return spannable
    val end = start + mainAmount.length
    val color = if (negative) R.color.loss else R.color.balance_main
    spannable.setSpan(
        ForegroundColorSpan(ContextCompat.getColor(context, color)),
        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    return spannable
}
