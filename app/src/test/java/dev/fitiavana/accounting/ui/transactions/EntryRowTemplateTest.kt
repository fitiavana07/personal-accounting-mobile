package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class EntryRowTemplateTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private val cash = Account(id = "cash", name = "Cash", type = "asset")
    private val food = Account(id = "food", name = "Food", type = "expense")

    private fun row() = EntryRowController(
        context = context,
        layoutInflater = LayoutInflater.from(context),
        parent = FrameLayout(context),
        viewModel = mock(),
        accounts = listOf(cash, food),
        instrumentsMap = emptyMap(),
        onChanged = {},
        onRemoveClicked = {},
        runInBackground = { it() },
        runOnUiThread = { it() }
    )

    @Test
    fun `no account is selected at first`() {
        assertNull(row().selectedAccountId())
    }

    @Test
    fun `selectedAccountId follows the spinner`() {
        val row = row()

        row.spinner.setSelection(2)

        assertEquals("food", row.selectedAccountId())
    }

    @Test
    fun `selectAccount picks the account by id`() {
        val row = row()

        assertTrue(row.selectAccount("cash"))

        assertEquals(1, row.spinner.selectedItemPosition)
        assertEquals("cash", row.selectedAccountId())
    }

    @Test
    fun `selectAccount is false for an unknown account and leaves the selection alone`() {
        val row = row()

        assertFalse(row.selectAccount("gone"))

        assertNull(row.selectedAccountId())
    }
}
