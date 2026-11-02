package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
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
import org.robolectric.shadows.ShadowToast

/** Tests for the Swap mode form: USD Wallet (From) buys BTC (To, intermediary USD) or ETH. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SwapControllerTest {

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
    private lateinit var toTextBalance: TextView
    private lateinit var fromAmountCode: TextView
    private lateinit var editFromAmount: EditText
    private lateinit var buttonAll: android.widget.Button
    private lateinit var toAmountCode: TextView
    private lateinit var editToAmount: EditText
    private lateinit var textAmountBase: TextView
    private lateinit var textRate: TextView
    private lateinit var switchFee: SwitchCompat
    private lateinit var feeGroup: View
    private lateinit var feeAmountCode: TextView
    private lateinit var editFee: EditText
    private lateinit var feeSpinner: Spinner
    private lateinit var textFeePreview: TextView
    private lateinit var textFeeReceived: TextView

    private val usd = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)
    private val btc = Instrument(code = "BTC", note = "", type = "crypto", decimalPlaces = 8)
    private val eth = Instrument(code = "ETH", note = "", type = "crypto", decimalPlaces = 4)
    private val instrumentsMap = mapOf("USD" to usd, "BTC" to btc, "ETH" to eth)

    private val cash = Account(id = "cash", name = "Cash", type = "asset")
    private val usdWallet = Account(id = "usd", name = "USD Wallet", type = "asset", instrumentCode = "USD")
    private val btcViaUsd = Account(
        id = "btc", name = "BTC", type = "asset", instrumentCode = "BTC", intermediaryInstrumentCode = "USD"
    )
    private val ethViaUsd = Account(
        id = "eth", name = "ETH", type = "asset", instrumentCode = "ETH", intermediaryInstrumentCode = "USD"
    )
    private val fees = Account(id = "fees", name = "Swap fees", type = "expense")
    private val accounts = listOf(cash, usdWallet, btcViaUsd, ethViaUsd, fees)
    private val balances = mapOf("cash" to 1L, "usd" to 1L, "btc" to 0L, "eth" to 0L)

    @Before
    fun setUp() {
        viewModel = mock()
        fromSpinner = Spinner(context)
        fromTextBalance = TextView(context)
        fromTextNewBalance = TextView(context)
        toTextNewBalance = TextView(context)
        fromTextZeroBalanceError = TextView(context)
        toSpinner = Spinner(context)
        toTextBalance = TextView(context)
        fromAmountCode = TextView(context)
        editFromAmount = EditText(context)
        buttonAll = android.widget.Button(context)
        toAmountCode = TextView(context)
        editToAmount = EditText(context)
        textAmountBase = TextView(context)
        textRate = TextView(context)
        switchFee = SwitchCompat(context)
        feeGroup = View(context)
        feeAmountCode = TextView(context)
        editFee = EditText(context)
        feeSpinner = Spinner(context)
        textFeePreview = TextView(context)
        textFeeReceived = TextView(context)
    }

    private fun controller(onChanged: () -> Unit = {}) = SwapController(
        context = context,
        viewModel = viewModel,
        instrumentsMap = instrumentsMap,
        fromSpinner = fromSpinner,
        fromTextBalance = fromTextBalance,
        fromTextNewBalance = fromTextNewBalance,
        fromTextZeroBalanceError = fromTextZeroBalanceError,
        toSpinner = toSpinner,
        toTextBalance = toTextBalance,
        toTextNewBalance = toTextNewBalance,
        fromAmountCode = fromAmountCode,
        editFromAmount = editFromAmount,
        buttonAll = buttonAll,
        toAmountCode = toAmountCode,
        editToAmount = editToAmount,
        textAmountBase = textAmountBase,
        textRate = textRate,
        switchFee = switchFee,
        feeGroup = feeGroup,
        feeAmountCode = feeAmountCode,
        editFee = editFee,
        feeSpinner = feeSpinner,
        textFeePreview = textFeePreview,
        textFeeReceived = textFeeReceived,
        onChanged = onChanged,
        runInBackground = { it() },
        runOnUiThread = { it() }
    ).also { it.populateSpinners(accounts, balances) }

    private fun stubBalance(id: String, balance: Long, instrument: Long = 0L, intermediary: Long = 0L) {
        whenever(viewModel.getBalance(id)).thenReturn(
            AccountBalance(
                accountId = id,
                balance = balance,
                instrumentBalance = instrument,
                intermediaryBalance = intermediary,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
    }

    private fun names(spinner: Spinner) =
        (1 until spinner.adapter.count).map { spinner.adapter.getItem(it).toString() }

    /** From: USD Wallet (position 1), holding 1,000.00 USD worth 4,500,000 Ar; To: BTC (position 1). */
    private fun selectUsdToBtc() {
        stubBalance("usd", balance = 4_500_000L, instrument = 100_000L)
        stubBalance("btc", balance = 0L)
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
    }

    @Test
    fun `from offers only funded swap-eligible accounts`() {
        controller()

        assertEquals(listOf("USD Wallet"), names(fromSpinner))
    }

    @Test
    fun `to offers nothing until from is selected then the matching accounts`() {
        controller()
        assertEquals(emptyList<String>(), names(toSpinner))

        fromSpinner.setSelection(1)

        assertEquals(listOf("BTC", "ETH"), names(toSpinner))
    }

    @Test
    fun `amount fields are labelled with their account's instrument code`() {
        controller()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(2)

        assertEquals("USD", fromAmountCode.text.toString())
        assertEquals("ETH", toAmountCode.text.toString())
    }

    @Test
    fun `collectEntries derives base from the from account rate and respects decimals`() {
        val controller = controller()
        selectUsdToBtc()
        editFromAmount.setText("100")
        editToAmount.setText("0.00123456")

        val entries = controller.collectEntries()!!

        // 100.00 USD = 10,000 units of 1,000.00 USD (100,000) valued 4,500,000 Ar -> 450,000 Ar
        assertEquals("usd", entries[0].accountId)
        assertEquals(450_000L, entries[0].creditAmount)
        assertEquals(10_000L, entries[0].instrumentCreditAmount)
        assertEquals("btc", entries[1].accountId)
        assertEquals(450_000L, entries[1].debitAmount)
        assertEquals(123_456L, entries[1].instrumentDebitAmount)
        assertEquals(10_000L, entries[1].intermediaryDebitAmount)
    }

    @Test
    fun `base preview shows the derived Ar amount`() {
        controller()
        selectUsdToBtc()

        editFromAmount.setText("100")

        assertEquals(View.VISIBLE, textAmountBase.visibility)
        assertTrue(textAmountBase.text.toString().contains("450,000"))
    }

    @Test
    fun `collectEntries toasts and returns null when accounts are missing`() {
        val controller = controller()

        assertNull(controller.collectEntries())
        assertEquals(context.getString(R.string.error_transfer_accounts_required), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `collectEntries toasts and returns null when an amount is missing`() {
        val controller = controller()
        selectUsdToBtc()
        editFromAmount.setText("100")

        assertNull(controller.collectEntries())
        assertEquals(context.getString(R.string.error_swap_amounts_required), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `zero from balance shows an error and blocks saving`() {
        val controller = controller()
        stubBalance("usd", balance = 0L, instrument = 0L)
        stubBalance("btc", balance = 0L)
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editFromAmount.setText("100")
        editToAmount.setText("1")

        assertEquals(View.VISIBLE, fromTextZeroBalanceError.visibility)
        assertNull(controller.collectEntries())
    }

    @Test
    fun `changing from clears a to selection that no longer fits`() {
        controller()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)

        fromSpinner.setSelection(0)

        assertEquals(emptyList<String>(), names(toSpinner))
        assertEquals(0, toSpinner.selectedItemPosition)
    }

    @Test
    fun `hasContent and clear track typed amounts and selections`() {
        val controller = controller()
        assertFalse(controller.hasContent())

        selectUsdToBtc()
        editToAmount.setText("1")
        assertTrue(controller.hasContent())

        controller.clear()

        assertFalse(controller.hasContent())
        assertEquals("", editToAmount.text.toString())
        assertEquals(0, fromSpinner.selectedItemPosition)
    }

    @Test
    fun `onChanged fires when an amount changes`() {
        var changes = 0
        controller { changes++ }

        editFromAmount.setText("1")
        editToAmount.setText("1")

        assertTrue(changes >= 2)
    }

    @Test
    fun `summaryEntries reflects typed amounts without validation`() {
        val controller = controller()
        selectUsdToBtc()
        editFromAmount.setText("100")

        val entries = controller.summaryEntries()

        assertEquals(450_000L, entries[0].creditAmount)
        assertNull(entries[1].instrumentDebitAmount)
    }

    /** From: 100.00 USD worth 4,500,000 Ar (45 Ar/unit); To: ETH (4 dp), receiving 0.0022 for 100.00 USD. */
    private fun selectUsdToEth() {
        stubBalance("usd", balance = 4_500_000L, instrument = 100_000L)
        stubBalance("eth", balance = 0L)
        fromSpinner.setSelection(1)
        toSpinner.setSelection(2)
        editFromAmount.setText("100")
        editToAmount.setText("0.0022")
    }

    @Test
    fun `fee is off by default with its inputs hidden`() {
        controller()

        assertFalse(switchFee.isChecked)
        assertEquals(View.GONE, feeGroup.visibility)
    }

    @Test
    fun `toggling the fee shows its inputs and toggling off clears them`() {
        controller()
        switchFee.isChecked = true
        assertEquals(View.VISIBLE, feeGroup.visibility)
        editFee.setText("0.0001")
        feeSpinner.setSelection(1)

        switchFee.isChecked = false

        assertEquals(View.GONE, feeGroup.visibility)
        assertEquals("", editFee.text.toString())
        assertEquals(0, feeSpinner.selectedItemPosition)
    }

    @Test
    fun `fee account spinner offers expense accounts and the fee is in the to instrument`() {
        controller()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(2)

        assertEquals(listOf("Swap fees"), names(feeSpinner))
        assertEquals("ETH", feeAmountCode.text.toString())
    }

    @Test
    fun `collectEntries with a fee nets the to side and debits the expense account`() {
        val controller = controller()
        selectUsdToEth()
        switchFee.isChecked = true
        editFee.setText("0.0001")
        feeSpinner.setSelection(1)

        val entries = controller.collectEntries()!!

        // fee 1 unit -> 1 * 10,000 / 22 = 455 USD units -> 455 * 45 = 20,475 Ar
        assertEquals(3, entries.size)
        assertEquals(450_000L, entries[0].creditAmount)
        assertEquals(429_525L, entries[1].debitAmount)
        assertEquals(21L, entries[1].instrumentDebitAmount)
        assertEquals(9_545L, entries[1].intermediaryDebitAmount)
        assertEquals("fees", entries[2].accountId)
        assertEquals(20_475L, entries[2].debitAmount)
    }

    @Test
    fun `collectEntries ignores a fee while the toggle is off`() {
        val controller = controller()
        selectUsdToEth()

        assertEquals(2, controller.collectEntries()!!.size)
    }

    @Test
    fun `collectEntries blocks a fee without an expense account`() {
        val controller = controller()
        selectUsdToEth()
        switchFee.isChecked = true
        editFee.setText("0.0001")

        assertNull(controller.collectEntries())
        assertEquals(
            context.getString(R.string.error_swap_fee_account_required),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `collectEntries blocks a fee that is not smaller than the received amount`() {
        val controller = controller()
        selectUsdToEth()
        switchFee.isChecked = true
        editFee.setText("0.0022")
        feeSpinner.setSelection(1)

        assertNull(controller.collectEntries())
        assertEquals(context.getString(R.string.error_swap_fee_invalid), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `fee preview and to new balance use the net amounts`() {
        controller()
        selectUsdToEth()
        switchFee.isChecked = true
        editFee.setText("0.0001")
        feeSpinner.setSelection(1)

        assertEquals(View.VISIBLE, textFeePreview.visibility)
        assertTrue(textFeePreview.text.toString().contains("20,475"))
        assertTrue(toTextNewBalance.text.toString().contains("0.0021"))
    }

    @Test
    fun `received line shows the real amounts added to to as instrument, intermediary and Ar`() {
        controller()
        selectUsdToEth()
        switchFee.isChecked = true
        editFee.setText("0.0001")
        feeSpinner.setSelection(1)

        assertEquals(View.VISIBLE, textFeeReceived.visibility)
        // net 21 units ETH, 10,000 - 455 USD units, 450,000 - 20,475 Ar
        assertEquals("+ 0.0021 ETH · 95.45 USD · 429,525 Ar", textFeeReceived.text.toString().replace(' ', ' '))
    }

    @Test
    fun `received line is hidden without a valid fee`() {
        controller()
        selectUsdToEth()
        assertEquals(View.GONE, textFeeReceived.visibility)

        switchFee.isChecked = true
        assertEquals(View.GONE, textFeeReceived.visibility)

        editFee.setText("0.0022")
        assertEquals(View.GONE, textFeeReceived.visibility)
    }

    @Test
    fun `clear resets the fee`() {
        val controller = controller()
        switchFee.isChecked = true

        controller.clear()

        assertFalse(switchFee.isChecked)
    }

    @Test
    fun `new balance previews are hidden until an account is selected`() {
        controller()

        assertEquals(View.GONE, fromTextNewBalance.visibility)
        assertEquals(View.GONE, toTextNewBalance.visibility)
    }

    @Test
    fun `new balance previews project both accounts from the typed amounts`() {
        controller()
        selectUsdToBtc()
        editFromAmount.setText("100")
        editToAmount.setText("0.5")

        // From: 4,500,000 - 450,000 Ar and 1,000.00 - 100.00 USD
        val from = fromTextNewBalance.text.toString()
        assertEquals(View.VISIBLE, fromTextNewBalance.visibility)
        assertTrue(from, from.contains("4,050,000"))
        assertTrue(from, from.contains("900"))
        // To: 0 + 450,000 Ar, 0 + 0.5 BTC and 0 + 100.00 USD cost basis
        val to = toTextNewBalance.text.toString()
        assertEquals(View.VISIBLE, toTextNewBalance.visibility)
        assertTrue(to, to.contains("450,000"))
        assertTrue(to, to.contains("0.5"))
        assertTrue(to, to.contains("100"))
    }

    @Test
    fun `new balance previews follow the amounts as they change`() {
        controller()
        selectUsdToBtc()
        editFromAmount.setText("100")
        val before = fromTextNewBalance.text.toString()

        editFromAmount.setText("200")

        assertFalse(fromTextNewBalance.text.toString() == before)
    }

    @Test
    fun `deselecting from hides both new balance previews`() {
        controller()
        selectUsdToBtc()
        editFromAmount.setText("100")

        fromSpinner.setSelection(0)

        assertEquals(View.GONE, fromTextNewBalance.visibility)
        assertEquals(View.GONE, toTextNewBalance.visibility)
    }

    @Test
    fun `ALL fills the from amount with the whole from instrument balance`() {
        val controller = controller()
        selectUsdToBtc() // 1,000.00 USD

        buttonAll.performClick()

        assertEquals(100_000L, controller.summaryEntries()[0].instrumentCreditAmount)
        assertEquals(4_500_000L, controller.summaryEntries()[0].creditAmount)
    }

    @Test
    fun `ALL does nothing without a from account or a balance`() {
        val controller = controller()
        buttonAll.performClick()
        assertEquals("", editFromAmount.text.toString())

        stubBalance("usd", balance = 0L, instrument = 0L)
        fromSpinner.setSelection(1)
        buttonAll.performClick()

        assertEquals("", editFromAmount.text.toString())
        assertNull(controller.summaryEntries()[0].instrumentCreditAmount)
    }

    @Test
    fun `rate preview is hidden until both amounts are typed`() {
        controller()
        selectUsdToBtc()
        assertEquals(View.GONE, textRate.visibility)

        editFromAmount.setText("100")
        assertEquals(View.GONE, textRate.visibility)
    }

    @Test
    fun `rate preview shows the price of one to unit in from units`() {
        controller()
        selectUsdToBtc()

        editFromAmount.setText("100")
        editToAmount.setText("0.002")

        assertEquals(View.VISIBLE, textRate.visibility)
        assertEquals("1 BTC = 50,000.0 USD", textRate.text.toString())
    }

    @Test
    fun `rate preview hides again when an amount is cleared`() {
        controller()
        selectUsdToBtc()
        editFromAmount.setText("100")
        editToAmount.setText("0.002")

        editToAmount.setText("")

        assertEquals(View.GONE, textRate.visibility)
    }
}
