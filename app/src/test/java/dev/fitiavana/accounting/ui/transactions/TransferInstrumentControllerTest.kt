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
import dev.fitiavana.accounting.ui.common.TransactionDisplay
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
class TransferInstrumentControllerTest {

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
    private lateinit var textAmountCode: TextView
    private lateinit var editTransferAmount: EditText
    private lateinit var buttonAll: Button
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

    private val usdViaEur = Account(
        id = "usd_via_eur", name = "USD via EUR", type = "asset",
        instrumentCode = "USD", intermediaryInstrumentCode = "EUR"
    )
    private val usdViaEurSavings = usdViaEur.copy(id = "usd_via_eur_savings", name = "USD via EUR Savings")

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
        textAmountCode = TextView(context)
        editTransferAmount = EditText(context)
        buttonAll = Button(context)
        textAmountBase = TextView(context)
    }

    private fun controller(onChanged: () -> Unit = {}): TransferController =
        TransferController(
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
            textAmountCode = textAmountCode,
            editTransferAmount = editTransferAmount,
            buttonAll = buttonAll,
            textAmountBase = textAmountBase,
            onChanged = onChanged,
            runInBackground = { it() },
            runOnUiThread = { it() }
        ).also {
            it.populateSpinners(
                listOf(usdWallet, usdSavings, eurWallet, usdViaEur, usdViaEurSavings),
                balances
            )
        }

    private var balances = mapOf(
        "usd_wallet" to 1L, "usd_savings" to 1L, "eur_wallet" to 1L,
        "usd_via_eur" to 1L, "usd_via_eur_savings" to 1L
    )

    @Test
    fun `to spinner excludes the account selected in from`() {
        controller()
        fromSpinner.setSelection(1)

        val names = (0 until toSpinner.adapter.count).map { toSpinner.adapter.getItem(it).toString() }

        assertEquals(listOf(context.getString(R.string.spinner_select_account), "USD Savings"), names)
    }

    @Test
    fun `from spinner hides accounts with a zero balance`() {
        balances = balances + ("usd_wallet" to 0L)
        controller()

        val names = (0 until fromSpinner.adapter.count).map { fromSpinner.adapter.getItem(it).toString() }

        assertEquals(
            listOf(
                context.getString(R.string.spinner_select_account),
                "USD Savings", "EUR Wallet", "USD via EUR", "USD via EUR Savings"
            ),
            names
        )
    }

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
        toSpinner.setSelection(1) // usd_savings (usd_wallet is excluded as the from account)
        editTransferAmount.setText("1.50")

        val entries = controller.collectEntries()
        assertEquals(2, entries?.size)
        val (totalDebit, totalCredit) = TransactionValidator.totals(entries!!)
        assertEquals(totalDebit, totalCredit)
        assertEquals(60_000L, totalDebit)
        assertEquals(150L, entries.first { it.accountId == "usd_wallet" }.instrumentCreditAmount)
        assertEquals(150L, entries.first { it.accountId == "usd_savings" }.instrumentDebitAmount)
    }

    private fun stubBalance(id: String, balance: Long, instrument: Long, intermediary: Long = 0L) {
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

    /** Selects usd_via_eur (From, position 4) then its twin (To, position 2 of [usd_via_eur, usd_via_eur_savings]). */
    private fun selectIntermediaryAccounts(amount: String) {
        stubBalance("usd_via_eur", balance = 400_000L, instrument = 1000L, intermediary = 500L)
        stubBalance("usd_via_eur_savings", balance = 0L, instrument = 0L, intermediary = 0L)
        fromSpinner.setSelection(4)
        toSpinner.setSelection(1)
        editTransferAmount.setText(amount)
    }

    @Test
    fun `ALL fills the amount with the from account instrument balance`() {
        stubBalance("usd_wallet", balance = 400_000L, instrument = 1250L)
        controller()
        fromSpinner.setSelection(1)

        buttonAll.performClick()

        assertEquals("12.5", editTransferAmount.text.toString())
    }

    @Test
    fun `ALL shows the whole from base balance and a zero new balance`() {
        stubBalance("usd_wallet", balance = 400_000L, instrument = 1250L)
        controller()
        fromSpinner.setSelection(1)

        buttonAll.performClick()

        assertEquals(context.getString(R.string.label_amount_base_ar, "400,000"), textAmountBase.text.toString())
        assertEquals(
            context.getString(R.string.label_new_balance_ar_instrument, "0", TransactionDisplay.formatInstrumentAmountNoWrap(0L, usdInstrument)),
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
    fun `ALL does nothing when the from instrument balance is zero`() {
        stubBalance("usd_wallet", balance = 0L, instrument = 0L)
        controller()
        fromSpinner.setSelection(1)
        editTransferAmount.setText("7")

        buttonAll.performClick()

        assertEquals("7", editTransferAmount.text.toString())
    }

    @Test
    fun `ALL notifies onChanged`() {
        stubBalance("usd_wallet", balance = 400_000L, instrument = 1250L)
        var changes = 0
        controller(onChanged = { changes++ })
        fromSpinner.setSelection(1)
        val before = changes

        buttonAll.performClick()

        assertTrue(changes > before)
    }

    @Test
    fun `to spinner only offers accounts sharing the from account's intermediary instrument`() {
        controller()

        fromSpinner.setSelection(4) // usd_via_eur

        // placeholder + usd_via_eur_savings; plain USD accounts and the from account are excluded
        assertEquals(2, toSpinner.adapter.count)
        assertEquals(usdViaEurSavings.name, toSpinner.adapter.getItem(1))
    }

    @Test
    fun `an intermediary transfer credits from and debits to by a proportionally derived intermediary amount`() {
        val controller = controller()
        selectIntermediaryAccounts("1.50") // 150 units -> 150 * 500 / 1000 = 75

        val entries = controller.collectEntries()!!
        val from = entries.first { it.accountId == "usd_via_eur" }
        val to = entries.first { it.accountId == "usd_via_eur_savings" }
        assertEquals(75L, from.intermediaryCreditAmount)
        assertNull(from.intermediaryDebitAmount)
        assertEquals(75L, to.intermediaryDebitAmount)
        assertNull(to.intermediaryCreditAmount)
    }

    @Test
    fun `an account without an intermediary records no intermediary amount`() {
        stubBalance("usd_wallet", balance = 400_000L, instrument = 1000L, intermediary = 500L)
        stubBalance("usd_savings", balance = 0L, instrument = 0L)
        val controller = controller()

        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editTransferAmount.setText("1.50")

        val entries = controller.collectEntries()!!
        assertTrue(entries.all { it.intermediaryCreditAmount == null && it.intermediaryDebitAmount == null })
        assertEquals("≈ 60,000 Ar", textAmountBase.text.toString())
    }

    @Test
    fun `balance and new balance lines append the intermediary part on both sides`() {
        controller()
        selectIntermediaryAccounts("1.50")

        assertEquals("Balance: 10.0\u00A0USD · 5.0\u00A0EUR · 400,000\u00A0Ar", fromTextBalance.text.toString())
        assertEquals(
            "New balance: 8.5\u00A0USD · 4.25\u00A0EUR · 340,000\u00A0Ar",
            fromTextNewBalance.text.toString()
        )
        assertEquals(
            "New balance: 1.5\u00A0USD · 0.75\u00A0EUR · 60,000\u00A0Ar",
            toTextNewBalance.text.toString()
        )
    }

    @Test
    fun `the amount preview appends the intermediary amount`() {
        controller()
        selectIntermediaryAccounts("1.50")

        assertEquals("≈ 0.75\u00A0EUR · 60,000 Ar", textAmountBase.text.toString())
    }

    @Test
    fun `to spinner only offers accounts sharing the from account's instrument`() {
        val controller = controller()

        fromSpinner.setSelection(1) // usd_wallet

        // placeholder + usd_savings; eur_wallet excluded (different instrument), usd_wallet is the from account
        assertEquals(2, toSpinner.adapter.count)
        assertEquals(usdSavings.name, toSpinner.adapter.getItem(1))
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
        toSpinner.setSelection(1)
        editTransferAmount.setText("1.50")

        assertNull(controller.collectEntries())
        assertEquals(
            context.getString(R.string.error_transfer_from_zero_balance),
            ShadowToast.getTextOfLatestToast()
        )
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
            "Balance: 10.0\u00A0USD · 400,000\u00A0Ar",
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
        toSpinner.setSelection(1)
        editTransferAmount.setText("1.50")

        assertEquals(View.VISIBLE, fromTextNewBalance.visibility)
        assertEquals(
            "New balance: 8.5\u00A0USD · 340,000\u00A0Ar",
            fromTextNewBalance.text.toString()
        )
        assertEquals(View.VISIBLE, toTextNewBalance.visibility)
        assertEquals(
            "New balance: 1.5\u00A0USD · 60,000\u00A0Ar",
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
    fun `selecting a from account with 0 instrument balance shows a persistent red error below the spinner`() {
        whenever(viewModel.getBalance("usd_savings")).thenReturn(
            AccountBalance(
                accountId = "usd_savings",
                balance = 0L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        controller()

        fromSpinner.setSelection(2) // usd_savings

        assertEquals(View.VISIBLE, fromTextZeroBalanceError.visibility)
        assertEquals(
            context.getString(R.string.error_transfer_from_zero_balance),
            fromTextZeroBalanceError.text.toString()
        )
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun `selecting a from account with nonzero instrument balance hides the zero-balance error`() {
        whenever(viewModel.getBalance("usd_savings")).thenReturn(
            AccountBalance(
                accountId = "usd_savings",
                balance = 0L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
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
        fromSpinner.setSelection(2) // usd_savings, 0 instrument balance
        assertEquals(View.VISIBLE, fromTextZeroBalanceError.visibility)

        fromSpinner.setSelection(1) // usd_wallet, nonzero instrument balance

        assertEquals(View.GONE, fromTextZeroBalanceError.visibility)
    }

    @Test
    fun `selecting a to account with 0 instrument balance does not show the from-balance error`() {
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
        controller()

        fromSpinner.setSelection(1) // usd_wallet, nonzero balance
        toSpinner.setSelection(1) // usd_savings, zero balance

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
