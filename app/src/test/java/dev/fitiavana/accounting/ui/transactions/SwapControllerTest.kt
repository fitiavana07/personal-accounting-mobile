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
    private lateinit var secondaryFeeSection: View
    private lateinit var switchSecondaryFee: SwitchCompat
    private lateinit var secondaryFeeGroup: View
    private lateinit var secondaryFeeAmountCode: TextView
    private lateinit var editSecondaryFee: EditText
    private lateinit var secondaryFeeSpinner: Spinner
    private lateinit var gainLossSection: View
    private lateinit var textGainLoss: TextView
    private lateinit var gainLossSpinner: Spinner

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
    private val btcWallet = Account(id = "btcw", name = "BTC Wallet", type = "asset", instrumentCode = "BTC")
    private val fees = Account(id = "fees", name = "Swap fees", type = "expense")
    private val commission = Account(id = "comm", name = "Commission", type = "expense")
    private val gain = Account(id = "gain", name = "Trading gain", type = "gain")
    private val loss = Account(id = "loss", name = "Trading loss", type = "loss")
    private val accounts = listOf(cash, usdWallet, btcViaUsd, ethViaUsd, btcWallet, fees, commission, gain, loss)

    // Cash is unfunded here so the instrument tests keep USD Wallet as From position 1.
    private val balances = mapOf("cash" to 0L, "usd" to 1L, "btc" to 0L, "eth" to 0L)

    // With a funded base account, From offers Cash (position 1) then USD Wallet.
    private val baseBalances = balances + ("cash" to 1L)

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
        secondaryFeeSection = View(context)
        switchSecondaryFee = SwitchCompat(context)
        secondaryFeeGroup = View(context)
        secondaryFeeAmountCode = TextView(context)
        editSecondaryFee = EditText(context)
        secondaryFeeSpinner = Spinner(context)
        gainLossSection = View(context)
        textGainLoss = TextView(context)
        gainLossSpinner = Spinner(context)
    }

    private fun controller(
        balances: Map<String, Long> = this.balances,
        onChanged: () -> Unit = {}
    ) = SwapController(
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
        secondaryFeeSection = secondaryFeeSection,
        switchSecondaryFee = switchSecondaryFee,
        secondaryFeeGroup = secondaryFeeGroup,
        secondaryFeeAmountCode = secondaryFeeAmountCode,
        editSecondaryFee = editSecondaryFee,
        secondaryFeeSpinner = secondaryFeeSpinner,
        gainLossSection = gainLossSection,
        textGainLoss = textGainLoss,
        gainLossSpinner = gainLossSpinner,
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

        assertEquals(listOf("Swap fees", "Commission"), names(feeSpinner))
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

    // ---- Base-currency From: Cash (500,000 Ar) buys BTC Wallet (8 dp, no intermediary) ----

    private fun baseController(onChanged: () -> Unit = {}) = controller(baseBalances, onChanged)

    /** From: Cash (position 1), To: BTC Wallet (position 2), 100,000 Ar for 0.002 BTC (200,000 units). */
    private fun selectCashToBtc() {
        stubBalance("cash", balance = 500_000L)
        stubBalance("btcw", balance = 0L)
        fromSpinner.setSelection(1)
        toSpinner.setSelection(2)
        editFromAmount.setText("100000")
        editToAmount.setText("0.002")
    }

    @Test
    fun `from offers a funded base account and to then offers instrument accounts without intermediary`() {
        baseController()

        assertEquals(listOf("Cash", "USD Wallet"), names(fromSpinner))
        fromSpinner.setSelection(1)
        assertEquals(listOf("USD Wallet", "BTC Wallet"), names(toSpinner))
    }

    @Test
    fun `selecting a base from shows the secondary fee and turns both fees on`() {
        baseController()
        assertEquals(View.GONE, secondaryFeeSection.visibility)
        assertFalse(switchFee.isChecked)

        fromSpinner.setSelection(1)

        assertEquals(View.VISIBLE, secondaryFeeSection.visibility)
        assertTrue(switchFee.isChecked)
        assertTrue(switchSecondaryFee.isChecked)
        assertEquals(View.VISIBLE, feeGroup.visibility)
        assertEquals(View.VISIBLE, secondaryFeeGroup.visibility)
    }

    @Test
    fun `switching from base to an instrument from hides the secondary fee and turns the fees off`() {
        baseController()
        fromSpinner.setSelection(1)

        fromSpinner.setSelection(2)

        assertEquals(View.GONE, secondaryFeeSection.visibility)
        assertFalse(switchSecondaryFee.isChecked)
        assertFalse(switchFee.isChecked)
    }

    @Test
    fun `switching between instrument froms keeps a fee the user turned on`() {
        controller()
        fromSpinner.setSelection(1)
        switchFee.isChecked = true

        fromSpinner.setSelection(0)
        fromSpinner.setSelection(1)

        assertTrue(switchFee.isChecked)
        assertEquals(View.GONE, secondaryFeeSection.visibility)
    }

    @Test
    fun `an instrument from never shows the secondary fee`() {
        controller()
        fromSpinner.setSelection(1)

        assertEquals(View.GONE, secondaryFeeSection.visibility)
        assertFalse(switchSecondaryFee.isChecked)
    }

    @Test
    fun `base from labels the from amount and the secondary fee with Ar and the fee with the to instrument`() {
        baseController()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(2)

        assertEquals("Ar", fromAmountCode.text.toString())
        assertEquals("Ar", secondaryFeeAmountCode.text.toString())
        assertEquals("BTC", toAmountCode.text.toString())
        assertEquals("BTC", feeAmountCode.text.toString())
    }

    @Test
    fun `ALL is hidden for a base from and visible again for an instrument from`() {
        baseController()
        assertEquals(View.VISIBLE, buttonAll.visibility)

        fromSpinner.setSelection(1)
        assertEquals(View.GONE, buttonAll.visibility)

        fromSpinner.setSelection(2)
        assertEquals(View.VISIBLE, buttonAll.visibility)
    }

    @Test
    fun `base from shows no zero balance error`() {
        baseController()
        stubBalance("cash", balance = 500_000L, instrument = 0L)

        fromSpinner.setSelection(1)

        assertEquals(View.GONE, fromTextZeroBalanceError.visibility)
    }

    @Test
    fun `base swap without fees credits from and debits to as cost basis with no intermediary`() {
        val controller = baseController()
        selectCashToBtc()
        switchFee.isChecked = false
        switchSecondaryFee.isChecked = false

        val entries = controller.collectEntries()!!

        assertEquals(2, entries.size)
        assertEquals("cash", entries[0].accountId)
        assertEquals(100_000L, entries[0].creditAmount)
        assertNull(entries[0].instrumentCreditAmount)
        assertEquals("btcw", entries[1].accountId)
        assertEquals(100_000L, entries[1].debitAmount)
        assertEquals(200_000L, entries[1].instrumentDebitAmount)
        assertNull(entries[1].intermediaryDebitAmount)
    }

    @Test
    fun `base swap with default fees left blank is the plain swap`() {
        val controller = baseController()
        selectCashToBtc()

        assertEquals(2, controller.collectEntries()!!.size)
    }

    @Test
    fun `base swap with a swap fee nets the to side at the swap price`() {
        val controller = baseController()
        selectCashToBtc()
        editFee.setText("0.00002")
        feeSpinner.setSelection(1)

        val entries = controller.collectEntries()!!

        // fee 2,000 units of 200,000 -> 1% -> 1,000 Ar
        assertEquals(3, entries.size)
        assertEquals(100_000L, entries[0].creditAmount)
        assertEquals(99_000L, entries[1].debitAmount)
        assertEquals(198_000L, entries[1].instrumentDebitAmount)
        assertEquals("fees", entries[2].accountId)
        assertEquals(1_000L, entries[2].debitAmount)
    }

    @Test
    fun `base swap with a secondary fee charges it on top of the from credit`() {
        val controller = baseController()
        selectCashToBtc()
        editSecondaryFee.setText("500")
        secondaryFeeSpinner.setSelection(1)

        val entries = controller.collectEntries()!!

        assertEquals(3, entries.size)
        assertEquals(100_500L, entries[0].creditAmount)
        assertEquals(100_000L, entries[1].debitAmount)
        assertEquals(200_000L, entries[1].instrumentDebitAmount)
        assertEquals("fees", entries[2].accountId)
        assertEquals(500L, entries[2].debitAmount)
    }

    @Test
    fun `base swap with both fees balances and keeps two expense entries`() {
        val controller = baseController()
        selectCashToBtc()
        editFee.setText("0.00002")
        feeSpinner.setSelection(1) // Swap fees
        editSecondaryFee.setText("500")
        secondaryFeeSpinner.setSelection(1) // only Commission is left

        val entries = controller.collectEntries()!!

        assertEquals(4, entries.size)
        assertEquals(100_500L, entries[0].creditAmount)
        assertEquals(99_000L, entries[1].debitAmount)
        assertEquals("fees", entries[2].accountId)
        assertEquals(1_000L, entries[2].debitAmount)
        assertEquals("comm", entries[3].accountId)
        assertEquals(500L, entries[3].debitAmount)
        assertTrue(TransactionValidator.validate(entries) is TransactionValidator.ValidationResult.Valid)
    }

    @Test
    fun `base swap blocks a secondary fee without an expense account`() {
        val controller = baseController()
        selectCashToBtc()
        editSecondaryFee.setText("500")

        assertNull(controller.collectEntries())
        assertEquals(
            context.getString(R.string.error_swap_secondary_fee_account_required),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `base swap ignores the secondary fee while its toggle is off`() {
        val controller = baseController()
        selectCashToBtc()
        editSecondaryFee.setText("500")
        switchSecondaryFee.isChecked = false

        assertEquals(2, controller.collectEntries()!!.size)
    }

    @Test
    fun `toggling the secondary fee off clears its amount and account`() {
        baseController()
        fromSpinner.setSelection(1)
        editSecondaryFee.setText("500")
        secondaryFeeSpinner.setSelection(1)

        switchSecondaryFee.isChecked = false

        assertEquals(View.GONE, secondaryFeeGroup.visibility)
        assertEquals("", editSecondaryFee.text.toString())
        assertEquals(0, secondaryFeeSpinner.selectedItemPosition)
    }

    @Test
    fun `the secondary fee account spinner never offers the swap fee account`() {
        baseController()
        fromSpinner.setSelection(1)
        assertEquals(listOf("Swap fees", "Commission"), names(secondaryFeeSpinner))

        feeSpinner.setSelection(1)
        assertEquals(listOf("Commission"), names(secondaryFeeSpinner))

        feeSpinner.setSelection(2)
        assertEquals(listOf("Swap fees"), names(secondaryFeeSpinner))
    }

    @Test
    fun `choosing the secondary account as the swap fee account resets the secondary selection`() {
        baseController()
        fromSpinner.setSelection(1)
        secondaryFeeSpinner.setSelection(2) // Commission

        feeSpinner.setSelection(2) // Commission

        assertEquals(0, secondaryFeeSpinner.selectedItemPosition)
    }

    @Test
    fun `a secondary account that is still offered stays selected`() {
        baseController()
        fromSpinner.setSelection(1)
        secondaryFeeSpinner.setSelection(2) // Commission

        feeSpinner.setSelection(1) // Swap fees

        assertEquals("Commission", secondaryFeeSpinner.selectedItem.toString())
    }

    @Test
    fun `base swap previews show the fee, the received line and the new balances in Ar`() {
        baseController()
        selectCashToBtc()
        editFee.setText("0.00002")
        feeSpinner.setSelection(1)
        editSecondaryFee.setText("500")

        assertEquals(View.GONE, textAmountBase.visibility)
        assertEquals("1 BTC = Ar 50,000,000", textRate.text.toString())
        assertTrue(textFeePreview.text.toString().contains("1,000"))
        assertEquals("+ 0.00198 BTC · 99,000 Ar", textFeeReceived.text.toString().replace(' ', ' '))
        // From: 500,000 - 100,500 Ar. To: 0 + 99,000 Ar and 0.00198 BTC
        assertTrue(fromTextNewBalance.text.toString().contains("399,500"))
        assertTrue(toTextNewBalance.text.toString().contains("99,000"))
        assertTrue(toTextNewBalance.text.toString().contains("0.00198"))
    }

    @Test
    fun `summaryEntries includes the typed secondary fee`() {
        val controller = baseController()
        selectCashToBtc()
        editSecondaryFee.setText("500")
        secondaryFeeSpinner.setSelection(1)

        assertEquals(100_500L, controller.summaryEntries()[0].creditAmount)
    }

    // ---- Overdraw: From can't go below zero ----

    private fun assertBlockedAsInsufficient(controller: SwapController) {
        assertNull(controller.collectEntries())
        assertEquals(
            context.getString(R.string.error_swap_insufficient_balance),
            ShadowToast.getTextOfLatestToast()
        )
    }

    /** The color of the emphasized amount in the From new balance line. */
    private fun fromNewBalanceColor(): Int {
        val text = fromTextNewBalance.text as android.text.Spanned
        return text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java).single().foregroundColor
    }

    private fun color(res: Int) = androidx.core.content.ContextCompat.getColor(context, res)

    @Test
    fun `base from new balance is red once the swap would overdraw it`() {
        baseController()
        selectCashToBtc() // 500,000 Ar
        editFromAmount.setText("499500")
        assertEquals(color(R.color.balance_main), fromNewBalanceColor())

        editSecondaryFee.setText("501") // total 500,001
        assertEquals(color(R.color.loss), fromNewBalanceColor())
        assertTrue(fromTextNewBalance.text.toString().contains("-1"))
    }

    @Test
    fun `base from new balance is normal when spending exactly the balance`() {
        baseController()
        selectCashToBtc()
        editFromAmount.setText("500000")

        assertEquals(color(R.color.balance_main), fromNewBalanceColor())
    }

    @Test
    fun `instrument from new balance is red once the amount exceeds its instrument balance`() {
        controller()
        selectUsdToBtc() // 1,000.00 USD
        editFromAmount.setText("1000")
        assertEquals(color(R.color.balance_main), fromNewBalanceColor())

        editFromAmount.setText("1000.01")

        assertEquals(color(R.color.loss), fromNewBalanceColor())
    }

    @Test
    fun `the to new balance stays in the normal color`() {
        baseController()
        selectCashToBtc()
        editFromAmount.setText("500001")

        val text = toTextNewBalance.text as android.text.Spanned
        val span = text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java).single()
        assertEquals(color(R.color.balance_main), span.foregroundColor)
    }

    @Test
    fun `base swap is blocked when the amount exceeds the from balance`() {
        val controller = baseController()
        selectCashToBtc() // 500,000 Ar
        editFromAmount.setText("500001")

        assertBlockedAsInsufficient(controller)
    }

    @Test
    fun `base swap is blocked when the secondary fee pushes the total over the from balance`() {
        val controller = baseController()
        selectCashToBtc()
        editFromAmount.setText("499800")
        editSecondaryFee.setText("500")
        secondaryFeeSpinner.setSelection(1)

        assertBlockedAsInsufficient(controller)
    }

    @Test
    fun `base swap may spend the whole from balance including the secondary fee`() {
        val controller = baseController()
        selectCashToBtc()
        editFromAmount.setText("499500")
        editSecondaryFee.setText("500")
        secondaryFeeSpinner.setSelection(1)

        assertEquals(500_000L, controller.collectEntries()!![0].creditAmount)
    }

    @Test
    fun `instrument swap is blocked when the amount exceeds the from instrument balance`() {
        val controller = controller()
        selectUsdToBtc() // 1,000.00 USD
        editFromAmount.setText("1000.01")
        editToAmount.setText("0.5")

        assertBlockedAsInsufficient(controller)
    }

    @Test
    fun `instrument swap may spend the whole from instrument balance`() {
        val controller = controller()
        selectUsdToBtc()
        editFromAmount.setText("1000")
        editToAmount.setText("0.5")

        assertEquals(4_500_000L, controller.collectEntries()!![0].creditAmount)
    }

    @Test
    fun `clear resets the secondary fee and hides it`() {
        val controller = baseController()
        fromSpinner.setSelection(1)
        editSecondaryFee.setText("500")

        controller.clear()

        assertFalse(switchSecondaryFee.isChecked)
        assertFalse(switchFee.isChecked)
        assertEquals(View.GONE, secondaryFeeSection.visibility)
        assertEquals("", editSecondaryFee.text.toString())
    }

    // SELL: BTC (From, via USD) holds 0.00001000 BTC worth 1,000,000 Ar with a cost basis of 1,000.00 USD;
    // selling 0.000004 BTC releases 400,000 Ar and 400.00 USD into USD Wallet (To).
    private fun sellController(): SwapController {
        val controller = controller(balances = mapOf("btc" to 1L))
        stubBalance("btc", balance = 1_000_000L, instrument = 1_000L, intermediary = 100_000L)
        stubBalance("usd", balance = 0L)
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editFromAmount.setText("0.000004")
        return controller
    }

    @Test
    fun `sell offers the from's intermediary instrument accounts as to`() {
        controller(balances = mapOf("btc" to 1L))
        fromSpinner.setSelection(1)

        assertEquals(listOf("BTC"), names(fromSpinner))
        assertEquals(listOf("USD Wallet"), names(toSpinner))
    }

    @Test
    fun `sell receiving exactly the cost basis has no gain or loss entry`() {
        val controller = sellController()
        editToAmount.setText("400")

        val entries = controller.collectEntries()!!

        assertEquals(2, entries.size)
        assertEquals(400_000L, entries[0].creditAmount)
        assertEquals(400L, entries[0].instrumentCreditAmount)
        assertEquals(40_000L, entries[0].intermediaryCreditAmount)
        assertEquals(400_000L, entries[1].debitAmount)
        assertEquals(40_000L, entries[1].instrumentDebitAmount)
        assertEquals(View.GONE, gainLossSection.visibility)
    }

    @Test
    fun `sell with a gain asks for a gain account and credits it`() {
        val controller = sellController()
        editToAmount.setText("500")

        assertEquals(View.VISIBLE, gainLossSection.visibility)
        assertEquals(listOf("Trading gain"), names(gainLossSpinner))
        assertTrue(textGainLoss.text.toString().contains("100,000"))
        assertNull(controller.collectEntries())
        assertEquals(context.getString(R.string.error_swap_gain_account_required), ShadowToast.getTextOfLatestToast())

        gainLossSpinner.setSelection(1)
        val entries = controller.collectEntries()!!

        assertEquals(500_000L, entries[1].debitAmount)
        assertEquals("gain", entries[2].accountId)
        assertEquals(100_000L, entries[2].creditAmount)
    }

    @Test
    fun `sell with a loss asks for a loss account and debits it`() {
        val controller = sellController()
        editToAmount.setText("300")

        assertEquals(View.VISIBLE, gainLossSection.visibility)
        assertEquals(listOf("Trading loss"), names(gainLossSpinner))
        assertNull(controller.collectEntries())
        assertEquals(context.getString(R.string.error_swap_loss_account_required), ShadowToast.getTextOfLatestToast())

        gainLossSpinner.setSelection(1)
        val entries = controller.collectEntries()!!

        assertEquals(300_000L, entries[1].debitAmount)
        assertEquals("loss", entries[2].accountId)
        assertEquals(100_000L, entries[2].debitAmount)
    }

    @Test
    fun `sell gain or loss section disappears when the amount matches the cost basis again`() {
        sellController()
        editToAmount.setText("500")
        editToAmount.setText("400")

        assertEquals(View.GONE, gainLossSection.visibility)
    }

    @Test
    fun `sell fee is deducted from the to amount and expensed`() {
        val controller = sellController()
        editToAmount.setText("500")
        gainLossSpinner.setSelection(1)
        switchFee.isChecked = true
        editFee.setText("50")
        feeSpinner.setSelection(1)

        val entries = controller.collectEntries()!!

        // fee 50.00 USD = 5,000 units at 10 Ar per unit of cost basis = 50,000 Ar
        assertEquals(450_000L, entries[1].debitAmount)
        assertEquals(45_000L, entries[1].instrumentDebitAmount)
        assertEquals("fees", entries[3].accountId)
        assertEquals(50_000L, entries[3].debitAmount)
    }

    @Test
    fun `sell hides the secondary fee`() {
        sellController()

        assertEquals(View.GONE, secondaryFeeSection.visibility)
        assertFalse(switchFee.isChecked)
    }

    @Test
    fun `sell is blocked when the amount exceeds the from instrument balance`() {
        val controller = sellController()
        editFromAmount.setText("0.00001001")
        editToAmount.setText("400")

        assertBlockedAsInsufficient(controller)
    }
}
