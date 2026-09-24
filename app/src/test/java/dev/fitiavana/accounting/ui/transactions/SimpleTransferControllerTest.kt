package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SimpleTransferControllerTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private lateinit var viewModel: AddTransactionViewModel

    private lateinit var fromSpinner: Spinner
    private lateinit var fromTextBalance: TextView
    private lateinit var fromTextNewBalance: TextView
    private lateinit var fromTextZeroBalanceError: TextView
    private lateinit var toSpinner: Spinner
    private lateinit var toTextBalance: TextView
    private lateinit var toTextNewBalance: TextView
    private lateinit var editTransferAmount: EditText

    private val cashAccount = Account(id = "cash", name = "Cash", type = "asset")
    private val bankAccount = Account(id = "bank", name = "Bank", type = "asset")

    @Before
    fun setUp() {
        viewModel = mock()
        fromSpinner = Spinner(context)
        fromTextBalance = TextView(context)
        fromTextNewBalance = TextView(context)
        fromTextZeroBalanceError = TextView(context)
        toSpinner = Spinner(context)
        toTextBalance = TextView(context)
        toTextNewBalance = TextView(context)
        editTransferAmount = EditText(context)
    }

    private fun controller(onChanged: () -> Unit = {}): SimpleTransferController =
        SimpleTransferController(
            context = context,
            viewModel = viewModel,
            fromSpinner = fromSpinner,
            fromTextBalance = fromTextBalance,
            fromTextNewBalance = fromTextNewBalance,
            fromTextZeroBalanceError = fromTextZeroBalanceError,
            toSpinner = toSpinner,
            toTextBalance = toTextBalance,
            toTextNewBalance = toTextNewBalance,
            editTransferAmount = editTransferAmount,
            onChanged = onChanged,
            runInBackground = { it() },
            runOnUiThread = { it() }
        ).also { it.populateSpinners(listOf(cashAccount, bankAccount)) }

    @Test
    fun `no accounts selected produces no entries`() {
        val controller = controller()
        assertNull(controller.collectEntries())
    }

    @Test
    fun `selecting from and to accounts and an amount produces two balanced entries`() {
        whenever(viewModel.getBalance("cash")).thenReturn(
            AccountBalance(accountId = "cash", balance = 500L, updatedAt = 0L, createdAt = 0L)
        )
        whenever(viewModel.getBalance("bank")).thenReturn(
            AccountBalance(accountId = "bank", balance = 0L, updatedAt = 0L, createdAt = 0L)
        )
        val controller = controller()

        fromSpinner.setSelection(1)
        toSpinner.setSelection(2)
        editTransferAmount.setText("100")

        val entries = controller.collectEntries()
        assertEquals(2, entries?.size)
        val (totalDebit, totalCredit) = TransactionValidator.totals(entries!!)
        assertEquals(totalDebit, totalCredit)
    }

    @Test
    fun `onChanged fires when the amount changes`() {
        var changed = false
        controller(onChanged = { changed = true })

        editTransferAmount.setText("50")

        assertTrue(changed)
    }

    @Test
    fun `balance preview becomes visible after an account is selected`() {
        whenever(viewModel.getBalance("cash")).thenReturn(
            AccountBalance(accountId = "cash", balance = 500L, updatedAt = 0L, createdAt = 0L)
        )
        controller()

        fromSpinner.setSelection(1)

        assertEquals(View.VISIBLE, fromTextBalance.visibility)
    }

    @Test
    fun `selecting a from account with 0 balance shows a persistent red error below the spinner`() {
        whenever(viewModel.getBalance("bank")).thenReturn(
            AccountBalance(accountId = "bank", balance = 0L, updatedAt = 0L, createdAt = 0L)
        )
        controller()

        fromSpinner.setSelection(2) // bank

        assertEquals(View.VISIBLE, fromTextZeroBalanceError.visibility)
        assertEquals(
            context.getString(R.string.error_transfer_from_zero_balance),
            fromTextZeroBalanceError.text.toString()
        )
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun `selecting a from account with nonzero balance hides the zero-balance error`() {
        whenever(viewModel.getBalance("bank")).thenReturn(
            AccountBalance(accountId = "bank", balance = 0L, updatedAt = 0L, createdAt = 0L)
        )
        whenever(viewModel.getBalance("cash")).thenReturn(
            AccountBalance(accountId = "cash", balance = 500L, updatedAt = 0L, createdAt = 0L)
        )
        controller()
        fromSpinner.setSelection(2) // bank, 0 balance
        assertEquals(View.VISIBLE, fromTextZeroBalanceError.visibility)

        fromSpinner.setSelection(1) // cash, nonzero balance

        assertEquals(View.GONE, fromTextZeroBalanceError.visibility)
    }

    @Test
    fun `collectEntries blocks and shows an error when the from account has 0 balance`() {
        whenever(viewModel.getBalance("bank")).thenReturn(
            AccountBalance(accountId = "bank", balance = 0L, updatedAt = 0L, createdAt = 0L)
        )
        val controller = controller()

        fromSpinner.setSelection(2) // bank
        toSpinner.setSelection(1) // cash
        editTransferAmount.setText("100")

        assertNull(controller.collectEntries())
        assertEquals(
            context.getString(R.string.error_transfer_from_zero_balance),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `selecting a to account with 0 balance does not show the from-balance error`() {
        whenever(viewModel.getBalance("bank")).thenReturn(
            AccountBalance(accountId = "bank", balance = 0L, updatedAt = 0L, createdAt = 0L)
        )
        controller()

        toSpinner.setSelection(2) // bank

        assertEquals(View.GONE, fromTextZeroBalanceError.visibility)
    }

    @Test
    fun `hasContent is false when untouched`() {
        val controller = controller()
        assertFalse(controller.hasContent())
    }

    @Test
    fun `hasContent is true once an amount is typed`() {
        val controller = controller()
        editTransferAmount.setText("10")
        assertTrue(controller.hasContent())
    }

    @Test
    fun `hasContent is true once a side is selected`() {
        val controller = controller()
        fromSpinner.setSelection(1)
        assertTrue(controller.hasContent())
    }
}
