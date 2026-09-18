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
class InstrumentIncomeControllerTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private lateinit var viewModel: AddTransactionViewModel

    private lateinit var assetSpinner: Spinner
    private lateinit var assetTextBalance: TextView
    private lateinit var assetTextNewBalance: TextView
    private lateinit var revenueSpinner: Spinner
    private lateinit var revenueTextBalance: TextView
    private lateinit var revenueTextNewBalance: TextView
    private lateinit var textAmountCode: TextView
    private lateinit var editIncomeAmount: EditText
    private lateinit var textAmountBase: TextView

    private val usdInstrument = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)
    private val eurInstrument = Instrument(code = "EUR", note = "", type = "fiat", decimalPlaces = 2)
    private val instrumentsMap = mapOf("USD" to usdInstrument, "EUR" to eurInstrument)

    private val usdWallet = Account(
        id = "usd_wallet", name = "USD Wallet", type = "asset", instrumentCode = "USD"
    )
    private val eurWallet = Account(
        id = "eur_wallet", name = "EUR Wallet", type = "asset", instrumentCode = "EUR"
    )
    private val salesRevenue = Account(id = "sales_revenue", name = "Sales Revenue", type = "revenue")
    private val otherRevenue = Account(id = "other_revenue", name = "Other Revenue", type = "revenue")

    @Before
    fun setUp() {
        viewModel = mock()
        assetSpinner = Spinner(context)
        assetTextBalance = TextView(context)
        assetTextNewBalance = TextView(context)
        revenueSpinner = Spinner(context)
        revenueTextBalance = TextView(context)
        revenueTextNewBalance = TextView(context)
        textAmountCode = TextView(context)
        editIncomeAmount = EditText(context)
        textAmountBase = TextView(context)
    }

    private fun controller(onChanged: () -> Unit = {}): InstrumentIncomeController =
        InstrumentIncomeController(
            context = context,
            viewModel = viewModel,
            instrumentsMap = instrumentsMap,
            assetSpinner = assetSpinner,
            assetTextBalance = assetTextBalance,
            assetTextNewBalance = assetTextNewBalance,
            revenueSpinner = revenueSpinner,
            revenueTextBalance = revenueTextBalance,
            revenueTextNewBalance = revenueTextNewBalance,
            textAmountCode = textAmountCode,
            editIncomeAmount = editIncomeAmount,
            textAmountBase = textAmountBase,
            onChanged = onChanged,
            runInBackground = { it() },
            runOnUiThread = { it() }
        ).also { it.populateSpinners(listOf(usdWallet, eurWallet, salesRevenue, otherRevenue)) }

    @Test
    fun `no accounts selected produces no entries`() {
        val controller = controller()
        assertNull(controller.collectEntries())
    }

    @Test
    fun `selecting asset and revenue accounts and an amount produces two balanced entries`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        whenever(viewModel.getBalance("sales_revenue")).thenReturn(
            AccountBalance(
                accountId = "sales_revenue",
                balance = 100_000L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        val controller = controller()

        assetSpinner.setSelection(1) // usd_wallet
        revenueSpinner.setSelection(1) // sales_revenue
        editIncomeAmount.setText("11.50") // new balance; prior instrument balance was 10.0

        val entries = controller.collectEntries()
        assertEquals(2, entries?.size)
        val (totalDebit, totalCredit) = TransactionValidator.totals(entries!!)
        assertEquals(totalDebit, totalCredit)
        assertEquals(60_000L, totalDebit)
        assertEquals(150L, entries.first { it.accountId == "usd_wallet" }.instrumentDebitAmount)
        assertNull(entries.first { it.accountId == "sales_revenue" }.instrumentCreditAmount)
        assertNull(entries.first { it.accountId == "sales_revenue" }.instrumentDebitAmount)
    }

    @Test
    fun `asset spinner only offers eligible asset accounts`() {
        val controller = controller()

        // placeholder + usd_wallet + eur_wallet
        assertEquals(3, assetSpinner.adapter.count)
        assertEquals(usdWallet.name, assetSpinner.adapter.getItem(1))
        assertEquals(eurWallet.name, assetSpinner.adapter.getItem(2))
    }

    @Test
    fun `revenue spinner offers only eligible revenue accounts regardless of asset selection`() {
        val controller = controller()

        assetSpinner.setSelection(1) // usd_wallet

        // placeholder + sales_revenue + other_revenue; unaffected by the asset selection
        assertEquals(3, revenueSpinner.adapter.count)
        assertEquals(salesRevenue.name, revenueSpinner.adapter.getItem(1))
        assertEquals(otherRevenue.name, revenueSpinner.adapter.getItem(2))
    }

    @Test
    fun `collectEntries fails when the asset account has no prior instrument balance`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 0L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        whenever(viewModel.getBalance("sales_revenue")).thenReturn(
            AccountBalance(
                accountId = "sales_revenue",
                balance = 0L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        val controller = controller()

        assetSpinner.setSelection(1)
        revenueSpinner.setSelection(1)
        editIncomeAmount.setText("1.50")

        assertNull(controller.collectEntries())
    }

    @Test
    fun `collectEntries fails when the new balance is not above the current balance`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        whenever(viewModel.getBalance("sales_revenue")).thenReturn(
            AccountBalance(
                accountId = "sales_revenue",
                balance = 0L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        val controller = controller()

        assetSpinner.setSelection(1)
        revenueSpinner.setSelection(1)
        editIncomeAmount.setText("10.00") // equal to the current instrument balance of 10.0

        assertNull(controller.collectEntries())
    }

    @Test
    fun `collectEntries fails when the new balance is below the current balance`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        whenever(viewModel.getBalance("sales_revenue")).thenReturn(
            AccountBalance(
                accountId = "sales_revenue",
                balance = 0L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        val controller = controller()

        assetSpinner.setSelection(1)
        revenueSpinner.setSelection(1)
        editIncomeAmount.setText("9.00") // below the current instrument balance of 10.0

        assertNull(controller.collectEntries())
    }

    @Test
    fun `collectEntries fails when the amount is blank`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        val controller = controller()

        assetSpinner.setSelection(1)
        revenueSpinner.setSelection(1)

        assertNull(controller.collectEntries())
    }

    @Test
    fun `onChanged fires when the amount changes`() {
        var changed = false
        controller(onChanged = { changed = true })

        editIncomeAmount.setText("0.50")

        assertTrue(changed)
    }

    @Test
    fun `asset balance preview shows base and instrument amounts on one line`() {
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

        assetSpinner.setSelection(1)

        assertEquals(View.VISIBLE, assetTextBalance.visibility)
        assertEquals("Balance: 400,000 Ar · 10.0 USD", assetTextBalance.text.toString())
    }

    @Test
    fun `revenue balance preview shows plain base currency amount only`() {
        whenever(viewModel.getBalance("sales_revenue")).thenReturn(
            AccountBalance(
                accountId = "sales_revenue",
                balance = 100_000L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        controller()

        revenueSpinner.setSelection(1)

        assertEquals(View.VISIBLE, revenueTextBalance.visibility)
        assertEquals("Balance: 100,000 Ar", revenueTextBalance.text.toString())
    }

    @Test
    fun `new balance preview for asset shows base and instrument amounts`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        whenever(viewModel.getBalance("sales_revenue")).thenReturn(
            AccountBalance(
                accountId = "sales_revenue",
                balance = 100_000L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        val controller = controller()

        assetSpinner.setSelection(1)
        revenueSpinner.setSelection(1)
        editIncomeAmount.setText("11.50") // new balance; prior instrument balance was 10.0

        assertEquals(View.VISIBLE, assetTextNewBalance.visibility)
        assertEquals("New balance: 460,000 Ar · 11.5 USD", assetTextNewBalance.text.toString())

        controller.collectEntries()
    }

    @Test
    fun `new balance previews default to the current balance when the field is blank`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        whenever(viewModel.getBalance("sales_revenue")).thenReturn(
            AccountBalance(
                accountId = "sales_revenue",
                balance = 100_000L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        controller()

        assetSpinner.setSelection(1)
        revenueSpinner.setSelection(1)

        assertEquals(View.VISIBLE, assetTextNewBalance.visibility)
        assertEquals("New balance: 400,000 Ar · 10.0 USD", assetTextNewBalance.text.toString())
        assertEquals(View.VISIBLE, revenueTextNewBalance.visibility)
        assertEquals("New balance: 100,000 Ar", revenueTextNewBalance.text.toString())
    }

    @Test
    fun `new balance preview for revenue shows plain base currency amount only`() {
        whenever(viewModel.getBalance("usd_wallet")).thenReturn(
            AccountBalance(
                accountId = "usd_wallet",
                balance = 400_000L,
                instrumentBalance = 1000L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        whenever(viewModel.getBalance("sales_revenue")).thenReturn(
            AccountBalance(
                accountId = "sales_revenue",
                balance = 100_000L,
                instrumentBalance = 0L,
                updatedAt = 0L,
                createdAt = 0L
            )
        )
        val controller = controller()

        assetSpinner.setSelection(1)
        revenueSpinner.setSelection(1)
        editIncomeAmount.setText("11.50") // new balance; prior instrument balance was 10.0

        assertEquals(View.VISIBLE, revenueTextNewBalance.visibility)
        assertEquals("New balance: 160,000 Ar", revenueTextNewBalance.text.toString())
    }

    @Test
    fun `new balance input shows the inferred transaction amount once the asset account's rate is known`() {
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

        assetSpinner.setSelection(1)
        editIncomeAmount.setText("11.50") // new balance; prior instrument balance was 10.0

        assertEquals(View.VISIBLE, textAmountBase.visibility)
        assertEquals("+ 60,000 Ar · 1.5 USD", textAmountBase.text.toString())
    }

    @Test
    fun `transaction amount preview is hidden while the new balance field is blank`() {
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

        assetSpinner.setSelection(1)

        assertEquals(View.GONE, textAmountBase.visibility)
    }

    @Test
    fun `transaction amount preview warns when the typed new balance is not above the current balance`() {
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

        assetSpinner.setSelection(1)
        editIncomeAmount.setText("9.00") // below the current instrument balance of 10.0

        assertEquals(View.VISIBLE, textAmountBase.visibility)
        assertEquals(
            "New balance must be above the current balance",
            textAmountBase.text.toString()
        )
    }

    @Test
    fun `transaction amount preview warns when the typed new balance equals the current balance`() {
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

        assetSpinner.setSelection(1)
        editIncomeAmount.setText("10.00") // equal to the current instrument balance of 10.0

        assertEquals(View.VISIBLE, textAmountBase.visibility)
        assertEquals(
            "New balance must be above the current balance",
            textAmountBase.text.toString()
        )
    }

    @Test
    fun `amount base currency equivalent is hidden when no asset account is selected`() {
        controller()

        editIncomeAmount.setText("1.50")

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
        editIncomeAmount.setText("1")
        assertTrue(controller.hasContent())
    }

    @Test
    fun `hasContent is true once a spinner is selected`() {
        val controller = controller()
        assetSpinner.setSelection(1)
        assertTrue(controller.hasContent())
    }
}
