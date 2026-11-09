package dev.fitiavana.accounting.ui.bankaccounts

import android.content.Context
import dev.fitiavana.accounting.R
import java.text.SimpleDateFormat
import java.util.Locale

/** How bank account dates are shown, shared by the list, the detail header and the transaction rows. */
internal object BankAccountDisplay {
    private const val DATE_PATTERN = "MMM dd, yyyy HH:mm"

    fun formatDate(millis: Long): String = SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(millis)

    /** "As of <date>", or a note that there is no transaction yet. */
    fun balanceDate(context: Context, balanceDate: Long?): String =
        if (balanceDate == null) context.getString(R.string.bank_account_no_transactions)
        else context.getString(R.string.bank_account_balance_date, formatDate(balanceDate))
}
