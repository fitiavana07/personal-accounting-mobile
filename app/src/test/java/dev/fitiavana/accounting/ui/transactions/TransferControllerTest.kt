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
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.templates.TemplateEntry
import dev.fitiavana.accounting.features.templates.TemplateModes
import dev.fitiavana.accounting.features.templates.TemplateSlot
import dev.fitiavana.accounting.features.templates.TemplateSlots
import dev.fitiavana.accounting.features.templates.TemplateWithEntries
import dev.fitiavana.accounting.features.templates.TransactionTemplate
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
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
class TransferControllerTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private lateinit var viewModel: AddTransactionViewModel
    private lateinit var fromSpinner: Spinner
    private lateinit var fromTextBalance: TextView
    private lateinit var fromTextNewBalance: TextView
    private lateinit var toTextNewBalance: TextView
    private lateinit var fromTextZeroBalanceError: TextView
    private lateinit var toSpinner: Spinner
    private lateinit var textAmountCode: TextView
    private lateinit var editAmount: EditText
    private lateinit var buttonAll: Button
    private lateinit var textAmountBase: TextView

    private val usd = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)
    private val cash = Account(id = "cash", name = "Cash", type = "asset")
    private val bank = Account(id = "bank", name = "Bank", type = "asset")
    private val usdWallet = Account(id = "usd", name = "USD Wallet", type = "asset", instrumentCode = "USD")
    private val usdSavings = Account(id = "usd2", name = "USD Savings", type = "asset", instrumentCode = "USD")
    private val accounts = listOf(cash, bank, usdWallet, usdSavings)
    private val balances = accounts.associate { it.id to 1L }

    @Before
    fun setUp() {
        viewModel = mock()
        fromSpinner = Spinner(context)
        fromTextBalance = TextView(context)
        fromTextNewBalance = TextView(context)
        toTextNewBalance = TextView(context)
        fromTextZeroBalanceError = TextView(context)
        toSpinner = Spinner(context)
        textAmountCode = TextView(context)
        editAmount = EditText(context)
        buttonAll = Button(context)
        textAmountBase = TextView(context)
    }

    private fun controller(onChanged: () -> Unit = {}) = TransferController(
        context = context,
        viewModel = viewModel,
        instrumentsMap = mapOf("USD" to usd),
        fromSpinner = fromSpinner,
        fromTextBalance = fromTextBalance,
        fromTextNewBalance = fromTextNewBalance,
        fromTextZeroBalanceError = fromTextZeroBalanceError,
        toSpinner = toSpinner,
        toTextBalance = TextView(context),
        toTextNewBalance = toTextNewBalance,
        textAmountCode = textAmountCode,
        editTransferAmount = editAmount,
        buttonAll = buttonAll,
        textAmountBase = textAmountBase,
        onChanged = onChanged,
        runInBackground = { it() },
        runOnUiThread = { it() }
    ).also { it.populateSpinners(accounts, balances) }

    private fun stubBalance(id: String, balance: Long, instrument: Long = 0L) {
        whenever(viewModel.getBalance(id)).thenReturn(
            AccountBalance(
                accountId = id, balance = balance, instrumentBalance = instrument,
                updatedAt = 0L, createdAt = 0L
            )
        )
    }

    private fun names(spinner: Spinner) =
        (1 until spinner.adapter.count).map { spinner.adapter.getItem(it).toString() }

    // Spinner positions: From = [Cash, Bank, USD Wallet, USD Savings]

    @Test
    fun `from spinner offers instrument-free and instrument accounts`() {
        controller()

        assertEquals(listOf("Cash", "Bank", "USD Wallet", "USD Savings"), names(fromSpinner))
    }

    @Test
    fun `an instrument-free from offers only other instrument-free accounts as to`() {
        controller()

        fromSpinner.setSelection(1) // Cash

        assertEquals(listOf("Bank"), names(toSpinner))
    }

    @Test
    fun `an instrument from offers only accounts sharing its instrument as to`() {
        controller()

        fromSpinner.setSelection(3) // USD Wallet

        assertEquals(listOf("USD Savings"), names(toSpinner))
    }

    @Test
    fun `an instrument-free transfer moves a base amount with no instrument fields`() {
        stubBalance("cash", balance = 5_000L)
        stubBalance("bank", balance = 0L)
        val controller = controller()

        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editAmount.setText("1200")

        val entries = controller.collectEntries()!!
        assertEquals(1_200L, entries.first { it.accountId == "cash" }.creditAmount)
        assertEquals(1_200L, entries.first { it.accountId == "bank" }.debitAmount)
        assertTrue(entries.all { it.instrumentCreditAmount == null && it.instrumentDebitAmount == null })
        assertEquals(context.getString(R.string.label_currency_ar), textAmountCode.text.toString())
        assertEquals(View.GONE, textAmountBase.visibility)
    }

    @Test
    fun `an instrument transfer derives base from the from balance and sets the code label`() {
        stubBalance("usd", balance = 400_000L, instrument = 1000L)
        stubBalance("usd2", balance = 0L)
        val controller = controller()

        fromSpinner.setSelection(3)
        toSpinner.setSelection(1)
        editAmount.setText("1.50")

        val entries = controller.collectEntries()!!
        assertEquals(60_000L, entries.first { it.accountId == "usd" }.creditAmount)
        assertEquals(150L, entries.first { it.accountId == "usd" }.instrumentCreditAmount)
        assertEquals(150L, entries.first { it.accountId == "usd2" }.instrumentDebitAmount)
        assertEquals("USD", textAmountCode.text.toString())
        assertEquals("≈ 60,000 Ar", textAmountBase.text.toString())
    }

    @Test
    fun `an instrument-free amount drops a decimal point`() {
        controller()
        fromSpinner.setSelection(1)

        editAmount.setText("12.5")

        assertEquals("125", editAmount.text.toString())
    }

    @Test
    fun `ALL fills the base balance for an instrument-free from`() {
        stubBalance("cash", balance = 5_000L)
        controller()
        fromSpinner.setSelection(1)

        buttonAll.performClick()

        assertEquals("5,000", editAmount.text.toString())
    }

    @Test
    fun `ALL fills the instrument balance for an instrument from`() {
        stubBalance("usd", balance = 400_000L, instrument = 1250L)
        controller()
        fromSpinner.setSelection(3)

        buttonAll.performClick()

        assertEquals("12.5", editAmount.text.toString())
    }

    @Test
    fun `switching the from account to another kind clears the amount`() {
        controller()
        fromSpinner.setSelection(1)
        editAmount.setText("1000")

        fromSpinner.setSelection(3)

        assertEquals("", editAmount.text.toString())
    }

    @Test
    fun `an instrument-free from with a zero balance shows the zero balance error`() {
        stubBalance("cash", balance = 0L)
        val controller = controller()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(0)

        assertEquals(View.VISIBLE, fromTextZeroBalanceError.visibility)
        assertNull(controller.collectEntries())
    }

    @Test
    fun `an instrument from with an amount of zero is rejected`() {
        stubBalance("usd", balance = 400_000L, instrument = 1000L)
        val controller = controller()
        fromSpinner.setSelection(3)
        toSpinner.setSelection(1)

        assertNull(controller.collectEntries())
    }

    private fun template(vararg slots: Pair<String, String>) = TemplateWithEntries(
        TransactionTemplate("t", "Move", TemplateModes.TRANSFER, 1L),
        slots.mapIndexed { i, (slot, account) -> TemplateEntry("e$i", "t", account, slot, i) }
    )

    @Test
    fun `templateSlots returns the chosen accounts`() {
        val controller = controller()
        assertNull(controller.templateSlots())

        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)

        assertEquals(
            listOf(TemplateSlot(TemplateSlots.FROM, "cash"), TemplateSlot(TemplateSlots.TO, "bank")),
            controller.templateSlots()
        )
    }

    @Test
    fun `applyTemplate works for both kinds and rejects a mismatched TO`() {
        val controller = controller()

        assertTrue(controller.applyTemplate(template(TemplateSlots.FROM to "cash", TemplateSlots.TO to "bank")))
        assertEquals("bank", controller.templateSlots()?.last()?.accountId)

        assertTrue(controller.applyTemplate(template(TemplateSlots.FROM to "usd", TemplateSlots.TO to "usd2")))
        assertEquals("usd2", controller.templateSlots()?.last()?.accountId)

        assertFalse(controller.applyTemplate(template(TemplateSlots.FROM to "cash", TemplateSlots.TO to "usd")))
    }

    @Test
    fun `hasContent and clear reset the form`() {
        val controller = controller()
        fromSpinner.setSelection(1)
        editAmount.setText("5")
        assertTrue(controller.hasContent())

        controller.clear()

        assertFalse(controller.hasContent())
    }

    /** The text the gold main-amount span covers in [view], or null when nothing is emphasized. */
    private fun emphasized(view: TextView): String? {
        val text = view.text as Spanned
        val span = text.getSpans(0, text.length, ForegroundColorSpan::class.java).singleOrNull() ?: return null
        assertEquals(ContextCompat.getColor(context, R.color.balance_main), span.foregroundColor)
        return text.substring(text.getSpanStart(span), text.getSpanEnd(span))
    }

    @Test
    fun `balance lines emphasize the base amount for an instrument-free account`() {
        stubBalance("cash", balance = 5_000L)
        stubBalance("bank", balance = 2_000L)
        controller()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editAmount.setText("1000")

        assertEquals("5,000\u00A0Ar", emphasized(fromTextBalance))
        assertEquals("4,000\u00A0Ar", emphasized(fromTextNewBalance))
        assertEquals("3,000\u00A0Ar", emphasized(toTextNewBalance))
    }

    @Test
    fun `balance lines emphasize the instrument amount for an account with an instrument`() {
        stubBalance("usd", balance = 400_000L, instrument = 1000L)
        stubBalance("usd2", balance = 0L)
        controller()
        fromSpinner.setSelection(3)
        toSpinner.setSelection(1)
        editAmount.setText("1.50")

        assertEquals("10.0\u00A0USD", emphasized(fromTextBalance))
        assertEquals("8.5\u00A0USD", emphasized(fromTextNewBalance))
        assertEquals("1.5\u00A0USD", emphasized(toTextNewBalance))
    }
}
