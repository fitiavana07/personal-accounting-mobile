package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
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

/**
 * Tests for the Transfer mode form. Three account sets exercise it: accounts without an instrument
 * ([baseController]), accounts with an instrument and intermediary instrument ([instrumentController]),
 * and a mix of both ([mixedController]).
 */
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

    private val cashAccount = Account(id = "cash", name = "Cash", type = "asset")
    private val bankAccount = Account(id = "bank", name = "Bank", type = "asset")

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

    private var baseBalances = mapOf("cash" to 1L, "bank" to 1L)
    private var instrumentBalances = mapOf(
        "usd_wallet" to 1L, "usd_savings" to 1L, "eur_wallet" to 1L,
        "usd_via_eur" to 1L, "usd_via_eur_savings" to 1L
    )
    private var mixedBalances = mapOf("cash" to 1L, "bank" to 1L, "usd_wallet" to 1L, "usd_savings" to 1L)

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

    private fun controller(
        accounts: List<Account>,
        balances: Map<String, Long>,
        onChanged: () -> Unit
    ): TransferController =
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
        ).also { it.populateSpinners(accounts, balances) }

    /** Accounts without an instrument: Cash, Bank. */
    private fun baseController(onChanged: () -> Unit = {}) =
        controller(listOf(cashAccount, bankAccount), baseBalances, onChanged)

    /** Accounts with an instrument: USD Wallet, USD Savings, EUR Wallet, USD via EUR (+ Savings). */
    private fun instrumentController(onChanged: () -> Unit = {}) = controller(
        listOf(usdWallet, usdSavings, eurWallet, usdViaEur, usdViaEurSavings), instrumentBalances, onChanged
    )

    /** Both kinds; From positions: Cash, Bank, USD Wallet, USD Savings. */
    private fun mixedController(onChanged: () -> Unit = {}) = controller(
        listOf(
            cashAccount, bankAccount,
            Account(id = "usd", name = "USD Wallet", type = "asset", instrumentCode = "USD"),
            Account(id = "usd2", name = "USD Savings", type = "asset", instrumentCode = "USD")
        ),
        mixedBalances.mapKeys { if (it.key == "usd_wallet") "usd" else if (it.key == "usd_savings") "usd2" else it.key },
        onChanged
    )

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

    /** Selects usd_via_eur (From, position 4) then its twin (To, position 1 of [usd_via_eur_savings]). */
    private fun selectIntermediaryAccounts(amount: String) {
        stubBalance("usd_via_eur", balance = 400_000L, instrument = 1000L, intermediary = 500L)
        stubBalance("usd_via_eur_savings", balance = 0L, instrument = 0L, intermediary = 0L)
        fromSpinner.setSelection(4)
        toSpinner.setSelection(1)
        editTransferAmount.setText(amount)
    }

    private fun spinnerNames(spinner: Spinner): List<String> =
        (0 until spinner.adapter.count).map { spinner.adapter.getItem(it).toString() }

    private fun names(spinner: Spinner) = spinnerNames(spinner).drop(1)

    @Test
    fun `from spinner hides accounts with a zero balance but to keeps them`() {
        baseBalances = mapOf("cash" to 0L, "bank" to 900L)
        baseController()

        assertEquals(
            listOf(context.getString(R.string.spinner_select_account), "Bank"),
            spinnerNames(fromSpinner)
        )
        fromSpinner.setSelection(1) // Bank

        assertEquals(
            listOf(context.getString(R.string.spinner_select_account), "Cash"),
            spinnerNames(toSpinner)
        )
    }

    @Test
    fun `to offers no account until from is selected`() {
        baseController()

        assertEquals(listOf(context.getString(R.string.spinner_select_account)), spinnerNames(toSpinner))
    }

    @Test
    fun `changing from restores the previous account in to and clears a clashing to selection`() {
        baseController()
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
    fun `deselecting from empties to and clears its selection`() {
        baseController()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)

        fromSpinner.setSelection(0)

        assertEquals(listOf(context.getString(R.string.spinner_select_account)), spinnerNames(toSpinner))
        assertEquals(0, toSpinner.selectedItemPosition)
    }

    @Test
    fun `from spinner selection maps to the right account after hiding zero baseBalances`() {
        baseBalances = mapOf("cash" to 0L, "bank" to 900L)
        stubBalance("bank", 800L)
        val controller = baseController()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editTransferAmount.setText("100")

        val entries = controller.collectEntries()!!

        assertEquals("bank", entries[0].accountId)
    }

    @Test
    fun `ALL fills the amount with the from account balance`() {
        stubBalance("cash", 12_500L)
        baseController()
        fromSpinner.setSelection(1)

        buttonAll.performClick()

        assertEquals(12_500L, EntryRowController.parseAmount(editTransferAmount))
    }

    @Test
    fun `ALL updates the from new balance preview to zero`() {
        stubBalance("cash", 500L)
        baseController()
        fromSpinner.setSelection(1)

        buttonAll.performClick()

        assertEquals(
            context.getString(R.string.label_new_balance_ar, "0"),
            fromTextNewBalance.text.toString()
        )
    }

    @Test
    fun `ALL does nothing when the from balance is zero`() {
        stubBalance("cash", 0L)
        baseController()
        fromSpinner.setSelection(1)
        editTransferAmount.setText("7")

        buttonAll.performClick()

        assertEquals("7", editTransferAmount.text.toString())
    }

    @Test
    fun `selecting from and to accounts and an amount produces two balanced entries`() {
        whenever(viewModel.getBalance("cash")).thenReturn(
            AccountBalance(accountId = "cash", balance = 500L, updatedAt = 0L, createdAt = 0L)
        )
        whenever(viewModel.getBalance("bank")).thenReturn(
            AccountBalance(accountId = "bank", balance = 0L, updatedAt = 0L, createdAt = 0L)
        )
        val controller = baseController()

        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editTransferAmount.setText("100")

        val entries = controller.collectEntries()
        assertEquals(2, entries?.size)
        val (totalDebit, totalCredit) = TransactionValidator.totals(entries!!)
        assertEquals(totalDebit, totalCredit)
    }

    @Test
    fun `balance lines keep the amount and Ar together with a non-breaking space`() {
        stubBalance("cash", 12_500L)
        stubBalance("bank", 700L)
        baseController()

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
        baseController()

        fromSpinner.setSelection(1)

        assertEquals(View.VISIBLE, fromTextBalance.visibility)
    }

    @Test
    fun `selecting a from account with 0 balance shows a persistent red error below the spinner`() {
        whenever(viewModel.getBalance("bank")).thenReturn(
            AccountBalance(accountId = "bank", balance = 0L, updatedAt = 0L, createdAt = 0L)
        )
        baseController()

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
        baseController()
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
        val controller = baseController()

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
        baseController()

        toSpinner.setSelection(2) // bank

        assertEquals(View.GONE, fromTextZeroBalanceError.visibility)
    }

    @Test
    fun `to spinner excludes the account selected in from`() {
        instrumentController()
        fromSpinner.setSelection(1)

        val names = (0 until toSpinner.adapter.count).map { toSpinner.adapter.getItem(it).toString() }

        assertEquals(listOf(context.getString(R.string.spinner_select_account), "USD Savings"), names)
    }

    @Test
    fun `from spinner hides accounts with a zero balance`() {
        instrumentBalances = instrumentBalances + ("usd_wallet" to 0L)
        instrumentController()

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
        val controller = instrumentController()
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
        val controller = instrumentController()

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

    @Test
    fun `ALL fills the amount with the from account instrument balance`() {
        stubBalance("usd_wallet", balance = 400_000L, instrument = 1250L)
        instrumentController()
        fromSpinner.setSelection(1)

        buttonAll.performClick()

        assertEquals("12.5", editTransferAmount.text.toString())
    }

    @Test
    fun `ALL shows the whole from base balance and a zero new balance`() {
        stubBalance("usd_wallet", balance = 400_000L, instrument = 1250L)
        instrumentController()
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
        instrumentController()
        editTransferAmount.setText("42")

        buttonAll.performClick()

        assertEquals("42", editTransferAmount.text.toString())
    }

    @Test
    fun `ALL does nothing when the from instrument balance is zero`() {
        stubBalance("usd_wallet", balance = 0L, instrument = 0L)
        instrumentController()
        fromSpinner.setSelection(1)
        editTransferAmount.setText("7")

        buttonAll.performClick()

        assertEquals("7", editTransferAmount.text.toString())
    }

    @Test
    fun `ALL notifies onChanged`() {
        stubBalance("usd_wallet", balance = 400_000L, instrument = 1250L)
        var changes = 0
        instrumentController(onChanged = { changes++ })
        fromSpinner.setSelection(1)
        val before = changes

        buttonAll.performClick()

        assertTrue(changes > before)
    }

    @Test
    fun `to spinner only offers accounts sharing the from account's intermediary instrument`() {
        instrumentController()

        fromSpinner.setSelection(4) // usd_via_eur

        // placeholder + usd_via_eur_savings; plain USD accounts and the from account are excluded
        assertEquals(2, toSpinner.adapter.count)
        assertEquals(usdViaEurSavings.name, toSpinner.adapter.getItem(1))
    }

    @Test
    fun `an intermediary transfer credits from and debits to by a proportionally derived intermediary amount`() {
        val controller = instrumentController()
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
        val controller = instrumentController()

        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editTransferAmount.setText("1.50")

        val entries = controller.collectEntries()!!
        assertTrue(entries.all { it.intermediaryCreditAmount == null && it.intermediaryDebitAmount == null })
        assertEquals("≈ 60,000 Ar", textAmountBase.text.toString())
    }

    @Test
    fun `balance and new balance lines append the intermediary part on both sides`() {
        instrumentController()
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
        instrumentController()
        selectIntermediaryAccounts("1.50")

        assertEquals("≈ 0.75\u00A0EUR · 60,000 Ar", textAmountBase.text.toString())
    }

    @Test
    fun `to spinner only offers accounts sharing the from account's instrument`() {
        val controller = instrumentController()

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
        val controller = instrumentController()

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
        instrumentController(onChanged = { changed = true })

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
        instrumentController()

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
        val controller = instrumentController()

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
        instrumentController()

        fromSpinner.setSelection(1)
        editTransferAmount.setText("1.50")

        assertEquals(View.VISIBLE, textAmountBase.visibility)
        assertEquals("≈ 60,000 Ar", textAmountBase.text.toString())
    }

    @Test
    fun `amount base currency equivalent is hidden when no from account is selected`() {
        instrumentController()

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
        instrumentController()

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
        instrumentController()
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
        instrumentController()

        fromSpinner.setSelection(1) // usd_wallet, nonzero balance
        toSpinner.setSelection(1) // usd_savings, zero balance

        assertEquals(View.GONE, fromTextZeroBalanceError.visibility)
    }

    @Test
    fun `hasContent is false when untouched`() {
        val controller = instrumentController()
        assertFalse(controller.hasContent())
    }

    @Test
    fun `hasContent is true once an amount is typed`() {
        val controller = instrumentController()
        editTransferAmount.setText("1")
        assertTrue(controller.hasContent())
    }

    @Test
    fun `hasContent is true once a side is selected`() {
        val controller = instrumentController()
        fromSpinner.setSelection(1)
        assertTrue(controller.hasContent())
    }

    @Test
    fun `from spinner offers instrument-free and instrument accounts`() {
        mixedController()

        assertEquals(listOf("Cash", "Bank", "USD Wallet", "USD Savings"), names(fromSpinner))
    }

    @Test
    fun `an instrument-free from offers only other instrument-free accounts as to`() {
        mixedController()

        fromSpinner.setSelection(1) // Cash

        assertEquals(listOf("Bank"), names(toSpinner))
    }

    @Test
    fun `an instrument-free transfer moves a base amount with no instrument fields`() {
        stubBalance("cash", balance = 5_000L)
        stubBalance("bank", balance = 0L)
        val controller = mixedController()

        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editTransferAmount.setText("1200")

        val entries = controller.collectEntries()!!
        assertEquals(1_200L, entries.first { it.accountId == "cash" }.creditAmount)
        assertEquals(1_200L, entries.first { it.accountId == "bank" }.debitAmount)
        assertTrue(entries.all { it.instrumentCreditAmount == null && it.instrumentDebitAmount == null })
        assertEquals(context.getString(R.string.label_currency_ar), textAmountCode.text.toString())
        assertEquals(View.GONE, textAmountBase.visibility)
    }

    @Test
    fun `an instrument-free amount drops a decimal point`() {
        mixedController()
        fromSpinner.setSelection(1)

        editTransferAmount.setText("12.5")

        assertEquals("125", editTransferAmount.text.toString())
    }

    @Test
    fun `switching the from account to another kind clears the amount`() {
        mixedController()
        fromSpinner.setSelection(1)
        editTransferAmount.setText("1000")

        fromSpinner.setSelection(3)

        assertEquals("", editTransferAmount.text.toString())
    }

    @Test
    fun `an instrument from with an amount of zero is rejected`() {
        stubBalance("usd", balance = 400_000L, instrument = 1000L)
        val controller = mixedController()
        fromSpinner.setSelection(3)
        toSpinner.setSelection(1)

        assertNull(controller.collectEntries())
    }

    @Test
    fun `hasContent and clear reset the form`() {
        val controller = mixedController()
        fromSpinner.setSelection(1)
        editTransferAmount.setText("5")
        assertTrue(controller.hasContent())

        controller.clear()

        assertFalse(controller.hasContent())
    }

    @Test
    fun `balance lines emphasize the base amount for an instrument-free account`() {
        stubBalance("cash", balance = 5_000L)
        stubBalance("bank", balance = 2_000L)
        mixedController()
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        editTransferAmount.setText("1000")

        assertEquals("5,000\u00A0Ar", emphasized(fromTextBalance))
        assertEquals("4,000\u00A0Ar", emphasized(fromTextNewBalance))
        assertEquals("3,000\u00A0Ar", emphasized(toTextNewBalance))
    }

    @Test
    fun `balance lines emphasize the instrument amount for an account with an instrument`() {
        stubBalance("usd", balance = 400_000L, instrument = 1000L)
        stubBalance("usd2", balance = 0L)
        mixedController()
        fromSpinner.setSelection(3)
        toSpinner.setSelection(1)
        editTransferAmount.setText("1.50")

        assertEquals("10.0\u00A0USD", emphasized(fromTextBalance))
        assertEquals("8.5\u00A0USD", emphasized(fromTextNewBalance))
        assertEquals("1.5\u00A0USD", emphasized(toTextNewBalance))
    }



    /** The text the gold main-amount span covers in [view], or null when nothing is emphasized. */
    private fun emphasized(view: TextView): String? {
        val text = view.text as Spanned
        val span = text.getSpans(0, text.length, ForegroundColorSpan::class.java).singleOrNull() ?: return null
        assertEquals(ContextCompat.getColor(context, R.color.balance_main), span.foregroundColor)
        return text.substring(text.getSpanStart(span), text.getSpanEnd(span))
    }
}
