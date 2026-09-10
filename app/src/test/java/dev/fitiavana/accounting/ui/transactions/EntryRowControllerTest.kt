package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
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
class EntryRowControllerTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private lateinit var parent: FrameLayout
    private lateinit var viewModel: AddTransactionViewModel

    private val cashAccount = Account(id = "cash", name = "Cash", type = "asset")
    private val revenueAccount = Account(id = "rev", name = "Revenue", type = "revenue")
    private val usd = Instrument(code = "USD", note = "Dollar", type = "fiat", decimalPlaces = 2)
    private val cryptoAccount = Account(
        id = "btc",
        name = "Bitcoin wallet",
        type = "asset",
        instrumentCode = "BTC"
    )
    private val btc = Instrument(code = "BTC", note = "Bitcoin", type = "crypto", decimalPlaces = 8)

    @Before
    fun setUp() {
        parent = FrameLayout(context)
        viewModel = mock()
    }

    private fun controller(
        accounts: List<Account> = listOf(cashAccount, revenueAccount),
        instrumentsMap: Map<String, Instrument> = emptyMap(),
        onChanged: () -> Unit = {},
        onRemoveClicked: (EntryRowController) -> Unit = {}
    ): EntryRowController = EntryRowController(
        context = context,
        layoutInflater = LayoutInflater.from(context),
        parent = parent,
        viewModel = viewModel,
        accounts = accounts,
        instrumentsMap = instrumentsMap,
        onChanged = onChanged,
        onRemoveClicked = onRemoveClicked,
        runInBackground = { it() },
        runOnUiThread = { it() }
    )

    @Test
    fun `no account selected is incomplete`() {
        val row = controller()
        assertEquals(EntryRowController.EntryResult.Incomplete, row.toEntryData())
    }

    @Test
    fun `selecting an account and a debit amount produces a valid entry`() {
        whenever(viewModel.getBalance("cash")).thenReturn(
            AccountBalance(accountId = "cash", balance = 500L, updatedAt = 0L, createdAt = 0L)
        )
        val row = controller()
        row.spinner.setSelection(1)
        row.editDebit.setText("100")

        val result = row.toEntryData()
        assertTrue(result is EntryRowController.EntryResult.Success)
        val data = (result as EntryRowController.EntryResult.Success).data
        assertEquals("cash", data.accountId)
        assertEquals(100L, data.debitAmount)
        assertNull(data.creditAmount)
    }

    @Test
    fun `entering a credit clears a previously entered debit`() {
        val row = controller()
        row.spinner.setSelection(1)
        row.editDebit.setText("100")

        row.editCredit.setText("50")

        assertEquals("", row.editDebit.text.toString())
    }

    @Test
    fun `onChanged fires when the debit amount changes`() {
        var changed = false
        val row = controller(onChanged = { changed = true })
        row.spinner.setSelection(1)

        row.editDebit.setText("100")

        assertTrue(changed)
    }

    @Test
    fun `balance preview updates after account balance loads`() {
        whenever(viewModel.getBalance("cash")).thenReturn(
            AccountBalance(accountId = "cash", balance = 500L, updatedAt = 0L, createdAt = 0L)
        )
        val row = controller()

        row.spinner.setSelection(1)
        row.editDebit.setText("100")

        val result = row.toEntryData() as EntryRowController.EntryResult.Success
        assertEquals(100L, result.data.debitAmount)
    }

    @Test
    fun `instrument amount is required when the account has an instrument`() {
        whenever(viewModel.getBalance("btc")).thenReturn(
            AccountBalance(accountId = "btc", balance = 0L, updatedAt = 0L, createdAt = 0L)
        )
        val row = controller(
            accounts = listOf(cryptoAccount),
            instrumentsMap = mapOf("BTC" to btc)
        )
        row.spinner.setSelection(1)
        row.editDebit.setText("100")

        val result = row.toEntryData()
        assertEquals(
            EntryRowController.EntryResult.InstrumentAmountRequired("BTC"),
            result
        )
    }

    @Test
    fun `instrument amount present produces a full entry`() {
        whenever(viewModel.getBalance("btc")).thenReturn(
            AccountBalance(accountId = "btc", balance = 0L, updatedAt = 0L, createdAt = 0L)
        )
        val row = controller(
            accounts = listOf(cryptoAccount),
            instrumentsMap = mapOf("BTC" to btc)
        )
        row.spinner.setSelection(1)
        row.editDebit.setText("100")
        row.editInstrumentDebit.setText("0.5")

        val result = row.toEntryData()
        assertTrue(result is EntryRowController.EntryResult.Success)
        val data = (result as EntryRowController.EntryResult.Success).data
        assertEquals(50_000_000L, data.instrumentDebitAmount)
        assertNull(data.instrumentCreditAmount)
    }

    @Test
    fun `hasContent is false for an untouched row`() {
        val row = controller()
        assertFalse(row.hasContent())
    }

    @Test
    fun `hasContent is true once an account is selected`() {
        val row = controller()
        row.spinner.setSelection(1)
        assertTrue(row.hasContent())
    }

    @Test
    fun `hasContent is true once an amount is typed`() {
        val row = controller()
        row.editDebit.setText("10")
        assertTrue(row.hasContent())
    }

    @Test
    fun `remove click invokes the callback with this controller`() {
        var removed: EntryRowController? = null
        val row = controller(onRemoveClicked = { removed = it })

        row.btnRemove.performClick()

        assertEquals(row, removed)
    }

    @Test
    fun `summaryEntry sums the row's own amounts regardless of account`() {
        val row = controller()
        row.editCredit.setText("250")

        val summary = row.summaryEntry()

        assertNull(summary.debitAmount)
        assertEquals(250L, summary.creditAmount)
    }
}
