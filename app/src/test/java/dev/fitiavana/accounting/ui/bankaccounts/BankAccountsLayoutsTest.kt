package dev.fitiavana.accounting.ui.bankaccounts

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import android.widget.RadioGroup
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class BankAccountsLayoutsTest {

    private val context: Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Accounting)

    private fun inflate(layout: Int): View = LayoutInflater.from(context).inflate(layout, null)

    @Test
    fun `the list has a transaction FAB and no add account button`() {
        val view = inflate(R.layout.activity_bank_accounts)

        assertNotNull(view.findViewById(R.id.fab_add_bank_transaction))
        assertEquals(0, context.resources.getIdentifier("button_add_bank_account", "id", context.packageName))
    }

    @Test
    fun `the overflow menu offers adding a bank account`() {
        val menu = PopupMenu(context, View(context)).menu
        MenuInflater(context).inflate(R.menu.menu_bank_accounts, menu)

        assertNotNull(menu.findItem(R.id.action_add_bank_account))
    }

    @Test
    fun `the transaction screen lists debit on the left, credit on the right, debit by default`() {
        val group = inflate(R.layout.activity_add_bank_account_transaction)
            .findViewById<RadioGroup>(R.id.group_bank_direction)

        assertEquals(R.id.radio_bank_debit, group.getChildAt(0).id)
        assertEquals(R.id.radio_bank_credit, group.getChildAt(1).id)
        assertEquals(R.id.radio_bank_debit, group.checkedRadioButtonId)
    }

    @Test
    fun `the account picker exists and is hidden by default`() {
        val picker = inflate(R.layout.activity_add_bank_account_transaction)
            .findViewById<View>(R.id.layout_bank_account_picker)

        assertEquals(View.GONE, picker.visibility)
    }
}
