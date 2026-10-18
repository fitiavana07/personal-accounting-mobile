package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.view.View
import android.widget.Button
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
    private lateinit var buttonAll: Button

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
        buttonAll = Button(context)
    }

    private var balances = mapOf("cash" to 1L, "bank" to 1L)

    private fun spinnerNames(spinner: Spinner): List<String> =
        (0 until spinner.adapter.count).map { spinner.adapter.getItem(it).toString() }

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
            buttonAll = buttonAll,
            onChanged = onChanged,
            runInBackground = { it() },
            runOnUiThread = { it() }
        ).also { it.populateSpinners(listOf(cashAccount, bankAccount), balances) }

    private fun stubBalance(id: String, balance: Long) {
        whenever(viewModel.getBalance(id)).thenReturn(
            AccountBalance(accountId = id, balance = balance, updatedAt = 0L, createdAt = 0L)
        )
    }

    @Test
    fun `from spinner hides accounts with a zero balance but to keeps them`() {
        balances = mapOf("cash" to 0L, "bank" to 900L)
        controller()

        assertEquals(
            listOf(context.getString(R.string.spinner_select_account), "Bank"),
            spinnerNames(fromSpinner)
        )
        assertEquals(
            listOf(context.getString(R.string.spinner_select_account), "Cash", "Bank"),
            spinnerNames(toSpinner)
        )
    }

    @Test
    fun `to spinner excludes the account selected in from`() {
        controller()

        fromSpinner.setSelection(1)

        assertEquals(
            listOf(context.getString(R.string.spinner_select_account), "Bank"),
            spinnerNames(toSpinner)
        )
    }

    @Test
    fun `changing from restores the previous account in to and clears a clashing to selection`() {
        controller()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        assertEquals("Bank", toSpinner.selectedItem.toString())

        fromSpinner.setSelection(2)

        assertEquals(
            listOf(context.getString(R.string.spinner_select_account), "Cash"),
            spinnerNames(toSpinner)
        )
        assertEquals(0, toSpinner.selectedItemPosition)
    }

    @Test
    fun `changing from keeps the to selection when it is still offered`() {
        controller()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)

        fromSpinner.setSelection(0)

        assertEquals("Bank", toSpinner.selectedItem.toString())
    }

    @Test
    fun `from spinner selection maps to the right account after hiding zero balances`() {
        balances = mapOf("cash" to 0L, "bank" to 900L)
        stubBalance("bank", 800L)
        val controller = controller()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editTransferAmount.setText("100")

        val entries = controller.collectEntries()!!

        assertEquals("bank", entries[0].accountId)
    }

    @Test
    fun `ALL fills the amount with the from account balance`() {
        stubBalance("cash", 12_500L)
        controller()
        fromSpinner.setSelection(1)

        buttonAll.performClick()

        assertEquals(12_500L, EntryRowController.parseAmount(editTransferAmount))
    }

    @Test
    fun `ALL updates the from new balance preview to zero`() {
        stubBalance("cash", 500L)
        controller()
        fromSpinner.setSelection(1)

        buttonAll.performClick()

        assertEquals(
            context.getString(R.string.label_new_balance_ar, "0"),
            fromTextNewBalance.text.toString()
        )
    }

    @Test
    fun `ALL does nothing when no from account is selected`() {
        controller()
        editTransferAmount.setText("42")

        buttonAll.performClick()

        assertEquals("42", editTransferAmount.text.toString())
    }

    @Test
    fun `ALL does nothing when the from balance is zero`() {
        stubBalance("cash", 0L)
        controller()
        fromSpinner.setSelection(1)
        editTransferAmount.setText("7")

        buttonAll.performClick()

        assertEquals("7", editTransferAmount.text.toString())
    }

    @Test
    fun `ALL notifies onChanged`() {
        stubBalance("cash", 500L)
        var changes = 0
        controller(onChanged = { changes++ })
        fromSpinner.setSelection(1)
        val before = changes

        buttonAll.performClick()

        assertTrue(changes > before)
    }

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
        toSpinner.setSelection(1)
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
    fun `balance lines keep the amount and Ar together with a non-breaking space`() {
        stubBalance("cash", 12_500L)
        stubBalance("bank", 700L)
        controller()

        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editTransferAmount.setText("500")

        assertEquals("Balance: 12,500\u00A0Ar", fromTextBalance.text.toString())
        assertEquals("New balance: 12,000\u00A0Ar", fromTextNewBalance.text.toString())
        assertEquals("Balance: 700\u00A0Ar", toTextBalance.text.toString())
        assertEquals("New balance: 1,200\u00A0Ar", toTextNewBalance.text.toString())
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
