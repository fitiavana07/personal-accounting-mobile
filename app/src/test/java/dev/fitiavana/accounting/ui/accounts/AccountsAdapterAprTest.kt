package dev.fitiavana.accounting.ui.accounts

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AccountsAdapterAprTest {

    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Accounting)

    private fun item(account: Account) = AccountListItem(
        account = account, balance = 1_000L, instrumentBalance = 0L, instrument = null,
        intermediaryBalance = 0L, intermediaryInstrument = null, updatedAt = null
    )

    private fun bind(account: Account): View {
        val adapter = AccountsAdapter(onItemClick = {})
        adapter.submitList(listOf(item(account)))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        return holder.itemView
    }

    @Test
    fun `an account with an APR shows it`() {
        val view = bind(Account(id = "1", name = "Earn", type = "asset", aprPercent = 5.5))

        val apr = view.findViewById<TextView>(R.id.text_account_apr)
        assertEquals(View.VISIBLE, apr.visibility)
        assertEquals("5.5% APR", apr.text.toString())
    }

    @Test
    fun `a whole-number APR has no decimals`() {
        val view = bind(Account(id = "1", name = "Earn", type = "asset", aprPercent = 12.0))

        assertEquals("12% APR", view.findViewById<TextView>(R.id.text_account_apr).text.toString())
    }

    @Test
    fun `an account without an APR hides the line`() {
        val view = bind(Account(id = "1", name = "Cash", type = "asset"))

        assertEquals(View.GONE, view.findViewById<TextView>(R.id.text_account_apr).visibility)
    }

    @Test
    fun `a recycled row clears a previous APR`() {
        val adapter = AccountsAdapter(onItemClick = {})
        adapter.submitList(
            listOf(
                item(Account(id = "1", name = "Earn", type = "asset", aprPercent = 5.5)),
                item(Account(id = "2", name = "Cash", type = "asset"))
            )
        )
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        adapter.onBindViewHolder(holder, 0)
        adapter.onBindViewHolder(holder, 1)

        assertEquals(View.GONE, holder.itemView.findViewById<TextView>(R.id.text_account_apr).visibility)
    }
}
