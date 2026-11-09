package dev.fitiavana.accounting.ui.bankaccounts

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.bankaccounts.BankAccountTransaction
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class BankAccountTransactionsAdapterTest {

    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Accounting)

    private fun bind(tx: BankAccountTransaction): View {
        val adapter = BankAccountTransactionsAdapter()
        adapter.submitList(listOf(tx))
        // ListAdapter diffs on a background thread unless the executor is idle; Robolectric runs it inline.
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        return holder.itemView
    }

    private fun tx(amount: Long, note: String = "rent", date: Long = 1_700_000_000_000L) =
        BankAccountTransaction("t", "a", amount, note, 1L, date)

    private fun textView(view: View, id: Int) = view.findViewById<TextView>(id)

    @Test
    fun `a credit shows a plus sign and the gain color`() {
        val amount = textView(bind(tx(1_500)), R.id.text_bank_tx_amount)

        assertEquals("+Ar 1,500", amount.text.toString())
        assertEquals(ContextCompat.getColor(context, R.color.gain), amount.currentTextColor)
    }

    @Test
    fun `a debit shows a minus sign and the loss color`() {
        val amount = textView(bind(tx(-250)), R.id.text_bank_tx_amount)

        assertEquals("-Ar 250", amount.text.toString())
        assertEquals(ContextCompat.getColor(context, R.color.loss), amount.currentTextColor)
    }

    @Test
    fun `the date and the note are shown`() {
        val view = bind(tx(5, note = "salary", date = 1_700_000_000_000L))

        val expected = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(1_700_000_000_000L)
        assertEquals(expected, textView(view, R.id.text_bank_tx_date).text.toString())
        assertEquals("salary", textView(view, R.id.text_bank_tx_note).text.toString())
        assertEquals(View.VISIBLE, textView(view, R.id.text_bank_tx_note).visibility)
    }

    @Test
    fun `a blank note is hidden`() {
        val view = bind(tx(5, note = ""))

        assertEquals(View.GONE, textView(view, R.id.text_bank_tx_note).visibility)
    }
}
