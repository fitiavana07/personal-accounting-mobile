package dev.fitiavana.accounting.ui.bankaccounts

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.bankaccounts.BankAccount
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class BankAccountsAdapterTest {

    private val context: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Accounting)

    private fun bind(account: BankAccount, clicked: MutableList<BankAccount> = mutableListOf()): View {
        val adapter = BankAccountsAdapter(onClick = { clicked += it })
        adapter.submit(listOf(account))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        return holder.itemView
    }

    private fun text(view: View, id: Int) = view.findViewById<TextView>(id).text.toString()

    @Test
    fun `a row shows the name, the balance and the date of the balance`() {
        val date = 1_700_000_000_000L
        val view = bind(BankAccount("a", "BOA", 1_234_567, date))

        assertEquals("BOA", text(view, R.id.text_bank_account_name))
        assertEquals("Ar 1,234,567", text(view, R.id.text_bank_account_balance))
        val expectedDate = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(date)
        assertEquals("As of $expectedDate", text(view, R.id.text_bank_account_date))
    }

    @Test
    fun `an account without transactions says so instead of showing a date`() {
        val view = bind(BankAccount("a", "BOA", 0, null))

        assertEquals("No transactions yet", text(view, R.id.text_bank_account_date))
    }

    @Test
    fun `a negative balance is shown with its sign`() {
        val view = bind(BankAccount("a", "BOA", -250, 1L))

        assertEquals("Ar -250", text(view, R.id.text_bank_account_balance))
    }

    @Test
    fun `tapping a row reports its account`() {
        val account = BankAccount("a", "BOA", 0, null)
        val clicked = mutableListOf<BankAccount>()

        bind(account, clicked).performClick()

        assertEquals(listOf(account), clicked)
    }
}
