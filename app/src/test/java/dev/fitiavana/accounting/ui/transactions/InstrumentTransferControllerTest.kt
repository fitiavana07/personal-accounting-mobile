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
import dev.fitiavana.accounting.features.instruments.Instrument
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class InstrumentTransferControllerTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private lateinit var viewModel: AddTransactionViewModel

    private lateinit var fromSpinner: Spinner
    private lateinit var fromTextBalance: TextView
    private lateinit var fromTextNewBalance: TextView
    private lateinit var toSpinner: Spinner
    private lateinit var toTextBalance: TextView
    private lateinit var toTextNewBalance: TextView
    private lateinit var textAmountCode: TextView
    private lateinit var editTransferAmount: EditText
    private lateinit var textAmountBase: TextView

    private val usdInstrument = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)
    private val eurInstrument = Instrument(code = "EUR", note = "", type = "fiat", decimalPlaces = 2)
    private val instrumentsMap = mapOf("USD" to usdInstrument, "EUR" to eurInstrument)

    private val usdWallet = Account(
        id = "usd_wallet", name = "USD Wallet", type = "asset", instrumentCode = "USD"
    )
    private val usdSavings = Account(
        id = "usd_savings", name = "USD Savings", type = "asset", instrumentCode = "USD"
    )
    private val eurWallet = Account(
        id = "eur_wallet", name = "EUR Wallet", type = "asset", instrumentCode = "EUR"
    )

    @Before
    fun setUp() {
        viewModel = mock()
        fromSpinner = Spinner(context)
        fromTextBalance = TextView(context)
        fromTextNewBalance = TextView(context)
        toSpinner = Spinner(context)
        toTextBalance = TextView(context)
        toTextNewBalance = TextView(context)
        textAmountCode = TextView(context)
        editTransferAmount = EditText(context)
        textAmountBase = TextView(context)
    }

    private fun controller(onChanged: () -> Unit = {}): InstrumentTransferController =
        InstrumentTransferController(
            context = context,
            viewModel = viewModel,
            instrumentsMap = instrumentsMap,
            fromSpinner = fromSpinner,
            fromTextBalance = fromTextBalance,
            fromTextNewBalance = fromTextNewBalance,
            toSpinner = toSpinner,
            toTextBalance = toTextBalance,
            toTextNewBalance = toTextNewBalance,
            textAmountCode = textAmountCode,
            editTransferAmount = editTransferAmount,
            textAmountBase = textAmountBase,
            onChanged = onChanged,
            runInBackground = { it() },
            runOnUiThread = { it() }
        ).also { it.populateSpinners(listOf(usdWallet, usdSavings, eurWallet)) }

    @Test
    fun `no accounts selected produces no entries`() {
        val controller = controller()
        assertNull(controller.collectEntries())
    }

    @Test
    fun `selecting matching from and to accounts and an amount produces two balanced entries`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        whenever(viewModel.getBalance("usd_savings")).thenReturn(
            AccountBalance(
                accountId = "usd_savings",
                balance = 0L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        val controller = controller()

        fromSpinner.setSelection(1) // usd_wallet
        toSpinner.setSelection(2) // usd_savings (usd_wallet itself is offered at position 1)
        editTransferAmount.setText("1.50")

        val entries = controller.collectEntries()
        assertEquals(2, entries?.size)
        val (totalDebit, totalCredit) = TransactionValidator.totals(entries!!)
        assertEquals(totalDebit, totalCredit)
        assertEquals(60_000L, totalDebit)
        assertEquals(150L, entries.first { it.accountId == "usd_wallet" }.instrumentCreditAmount)
        assertEquals(150L, entries.first { it.accountId == "usd_savings" }.instrumentDebitAmount)
    }

    @Test
    fun `to spinner only offers accounts sharing the from account's instrument`() {
        val controller = controller()

        fromSpinner.setSelection(1) // usd_wallet

        // placeholder + usd_wallet + usd_savings; eur_wallet excluded (different instrument)
        assertEquals(3, toSpinner.adapter.count)
        assertEquals(usdWallet.name, toSpinner.adapter.getItem(1))
        assertEquals(usdSavings.name, toSpinner.adapter.getItem(2))
    }

    @Test
    fun `collectEntries fails when the from account has no prior instrument balance`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 0L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        whenever(viewModel.getBalance("usd_savings")).thenReturn(
            AccountBalance(
                accountId = "usd_savings",
                balance = 0L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        val controller = controller()

        fromSpinner.setSelection(1)
        toSpinner.setSelection(2)
        editTransferAmount.setText("1.50")

        assertNull(controller.collectEntries())
    }

    @Test
    fun `onChanged fires when the amount changes`() {
        var changed = false
        controller(onChanged = { changed = true })

        editTransferAmount.setText("0.50")

        assertTrue(changed)
    }

    @Test
    fun `balance preview shows base and instrument amounts on one line each`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        controller()

        fromSpinner.setSelection(1)

        assertEquals(View.VISIBLE, fromTextBalance.visibility)
        assertEquals(
            "Balance: 400,000 Ar · 10.0 USD",
            fromTextBalance.text.toString()
        )
    }

    @Test
    fun `new balance preview shows base and instrument amounts on one line`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        whenever(viewModel.getBalance("usd_savings")).thenReturn(
            AccountBalance(
                accountId = "usd_savings",
                balance = 0L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        val controller = controller()

        fromSpinner.setSelection(1)
        toSpinner.setSelection(2)
        editTransferAmount.setText("1.50")

        assertEquals(View.VISIBLE, fromTextNewBalance.visibility)
        assertEquals(
            "New balance: 340,000 Ar · 8.5 USD",
            fromTextNewBalance.text.toString()
        )
        assertEquals(View.VISIBLE, toTextNewBalance.visibility)
        assertEquals(
            "New balance: 60,000 Ar · 1.5 USD",
            toTextNewBalance.text.toString()
        )

        controller.collectEntries()
    }

    @Test
    fun `amount input shows its base currency equivalent once the from account's rate is known`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        controller()

        fromSpinner.setSelection(1)
        editTransferAmount.setText("1.50")

        assertEquals(View.VISIBLE, textAmountBase.visibility)
        assertEquals("≈ 60,000 Ar", textAmountBase.text.toString())
    }

    @Test
    fun `amount base currency equivalent is hidden when no from account is selected`() {
        controller()

        editTransferAmount.setText("1.50")

        assertEquals(View.GONE, textAmountBase.visibility)
    }

    @Test
    fun `hasContent is false when untouched`() {
        val controller = controller()
        assertFalse(controller.hasContent())
    }

    @Test
    fun `hasContent is true once an amount is typed`() {
        val controller = controller()
        editTransferAmount.setText("1")
        assertTrue(controller.hasContent())
    }

    @Test
    fun `hasContent is true once a side is selected`() {
        val controller = controller()
        fromSpinner.setSelection(1)
        assertTrue(controller.hasContent())
    }
}
