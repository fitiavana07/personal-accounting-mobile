package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.view.View
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AccountSideControllerTest {

    private lateinit var context: Context

    private val usd = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)
    private lateinit var instrumentsMap: Map<String, Instrument>

    private val accountNoInstrument =
        Account(id = "a1", name = "Cash", type = "asset", instrumentCode = null)
    private val accountUsd =
        Account(id = "a2", name = "USD Wallet", type = "asset", instrumentCode = "USD")
    private val accountUnknownInstrument =
        Account(id = "a3", name = "Weird", type = "asset", instrumentCode = "XYZ")

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Accounting
        )
        instrumentsMap = mapOf("USD" to usd)
    }

    private fun controller(
        instrumentsMap: Map<String, Instrument> = this.instrumentsMap,
        getBalance: (String) -> Pair<Long, Long>? = { null },
        spinner: Spinner = Spinner(context),
        textBalance: TextView = TextView(context),
        isDebit: Boolean = true,
        onAccountSelected: (Account?) -> Unit = {},
        onBalanceLoaded: () -> Unit = {},
        runInBackground: (() -> Unit) -> Unit = { it() },
        runOnUiThread: (() -> Unit) -> Unit = { it() }
    ): AccountSideController {
        return AccountSideController(
            context = context,
            instrumentsMap = instrumentsMap,
            getBalance = getBalance,
            spinner = spinner,
            textBalance = textBalance,
            isDebit = isDebit,
            onAccountSelected = onAccountSelected,
            onBalanceLoaded = onBalanceLoaded,
            runInBackground = runInBackground,
            runOnUiThread = runOnUiThread
        )
    }

    /** Fires the spinner's selection listener as if the user picked `position`. */
    private fun select(spinner: Spinner, position: Int) {
        spinner.setSelection(position)
    }

    private fun selectNothing(spinner: Spinner) {
        spinner.onItemSelectedListener?.onNothingSelected(spinner)
    }

    // --- 1. Constructor wiring ---

    @Test
    fun constructor_wiresSpinnerOnItemSelectedListener() {
        val spinner = Spinner(context)
        assertNull(spinner.onItemSelectedListener)

        controller(spinner = spinner)

        assertNotNull(spinner.onItemSelectedListener)
    }

    // --- 2. populate() ---

    @Test
    fun populate_setsAccountsBuildsPlaceholderAdapterAndSelectsPlaceholder() {
        val spinner = Spinner(context)
        val sut = controller(spinner = spinner)
        val accounts = listOf(accountNoInstrument, accountUsd)

        sut.populate(accounts)

        assertEquals(accounts, sut.accounts)
        val adapter = spinner.adapter
        assertNotNull(adapter)
        assertEquals(3, adapter!!.count)
        assertEquals(context.getString(R.string.spinner_select_account), adapter.getItem(0))
        assertEquals(accountNoInstrument.name, adapter.getItem(1))
        assertEquals(accountUsd.name, adapter.getItem(2))
        assertEquals(0, spinner.selectedItemPosition)
    }

    @Test
    fun populate_calledAgain_rebuildsAdapterForNewAccountsAndResetsSelection() {
        val spinner = Spinner(context)
        val sut = controller(spinner = spinner)
        sut.populate(listOf(accountNoInstrument))

        sut.populate(listOf(accountUsd, accountUnknownInstrument))

        assertEquals(listOf(accountUsd, accountUnknownInstrument), sut.accounts)
        val adapter = spinner.adapter
        assertEquals(3, adapter!!.count)
        assertEquals(accountUsd.name, adapter.getItem(1))
        assertEquals(accountUnknownInstrument.name, adapter.getItem(2))
        assertEquals(0, spinner.selectedItemPosition)
    }

    // --- 3. Selecting a real account ---

    @Test
    fun selectingAccount_withoutInstrument_setsAccountAppliesBalanceAndNotifiesCallbacks() {
        val spinner = Spinner(context)
        val textBalance = TextView(context)
        var selectedNotified: Account? = null
        var balanceLoadedCalled = false
        val sut = controller(
            spinner = spinner,
            textBalance = textBalance,
            getBalance = { id -> if (id == accountNoInstrument.id) Pair(1000L, 1000L) else null },
            onAccountSelected = { selectedNotified = it },
            onBalanceLoaded = { balanceLoadedCalled = true }
        )
        sut.populate(listOf(accountNoInstrument))

        select(spinner, 1)

        assertEquals(accountNoInstrument, sut.account)
        assertEquals(accountNoInstrument, selectedNotified)
        assertEquals(1000L, sut.balance)
        assertEquals(1000L, sut.instrumentBalance)
        assertEquals(
            context.getString(R.string.label_balance_ar, TransactionDisplay.formatAmount(1000L)),
            textBalance.text.toString()
        )
        assertEquals(View.VISIBLE, textBalance.visibility)
        assertTrue(balanceLoadedCalled)
    }

    @Test
    fun selectingAccount_withKnownInstrument_usesInstrumentFormattedBalanceText() {
        val spinner = Spinner(context)
        val textBalance = TextView(context)
        val sut = controller(
            spinner = spinner,
            textBalance = textBalance,
            getBalance = { id -> if (id == accountUsd.id) Pair(2000L, 20L) else null }
        )
        sut.populate(listOf(accountUsd))

        select(spinner, 1)

        assertEquals(2000L, sut.balance)
        assertEquals(20L, sut.instrumentBalance)
        assertEquals(
            context.getString(
                R.string.label_balance_ar_instrument,
                TransactionDisplay.formatAmount(2000L),
                TransactionDisplay.formatInstrumentAmount(20L, usd)
            ),
            textBalance.text.toString()
        )
        assertEquals(View.VISIBLE, textBalance.visibility)
    }

    @Test
    fun selectingAccount_withInstrumentCodeNotInMap_fallsBackToPlainBalanceText() {
        val spinner = Spinner(context)
        val textBalance = TextView(context)
        val sut = controller(
            spinner = spinner,
            textBalance = textBalance,
            getBalance = { id -> if (id == accountUnknownInstrument.id) Pair(500L, 500L) else null }
        )
        sut.populate(listOf(accountUnknownInstrument))

        select(spinner, 1)

        assertEquals(
            context.getString(R.string.label_balance_ar, TransactionDisplay.formatAmount(500L)),
            textBalance.text.toString()
        )
        assertEquals(View.VISIBLE, textBalance.visibility)
    }

    @Test
    fun selectingAccount_whenGetBalanceReturnsNull_zerosBalanceButStillShowsText() {
        val spinner = Spinner(context)
        val textBalance = TextView(context)
        var balanceLoadedCalled = false
        val sut = controller(
            spinner = spinner,
            textBalance = textBalance,
            getBalance = { null },
            onBalanceLoaded = { balanceLoadedCalled = true }
        )
        sut.populate(listOf(accountNoInstrument))

        select(spinner, 1)

        assertEquals(0L, sut.balance)
        assertEquals(0L, sut.instrumentBalance)
        assertEquals(
            context.getString(R.string.label_balance_ar, TransactionDisplay.formatAmount(0L)),
            textBalance.text.toString()
        )
        assertEquals(View.VISIBLE, textBalance.visibility)
        assertTrue(balanceLoadedCalled)
    }

    // --- 4. Selecting the placeholder / onNothingSelected ---

    @Test
    fun selectingPlaceholder_clearsAccountHidesBalanceAndDoesNotCallGetBalance() {
        val spinner = Spinner(context)
        val textBalance = TextView(context)
        var getBalanceCallCount = 0
        var lastNotified: Account? = accountNoInstrument // sentinel, expect it to become null
        val sut = controller(
            spinner = spinner,
            textBalance = textBalance,
            getBalance = { id ->
                getBalanceCallCount++
                if (id == accountNoInstrument.id) Pair(1000L, 1000L) else null
            },
            onAccountSelected = { lastNotified = it }
        )
        sut.populate(listOf(accountNoInstrument))
        select(spinner, 1)
        assertEquals(1, getBalanceCallCount)
        assertEquals(View.VISIBLE, textBalance.visibility)

        select(spinner, 0)

        assertNull(sut.account)
        assertNull(lastNotified)
        assertEquals(1, getBalanceCallCount) // not called again
        assertEquals(View.GONE, textBalance.visibility)
    }

    @Test
    fun onNothingSelected_clearsAccountAndHidesBalance() {
        val spinner = Spinner(context)
        val textBalance = TextView(context)
        var lastNotified: Account? = accountNoInstrument
        val sut = controller(
            spinner = spinner,
            textBalance = textBalance,
            getBalance = { Pair(1000L, 1000L) },
            onAccountSelected = { lastNotified = it }
        )
        sut.populate(listOf(accountNoInstrument))
        select(spinner, 1)
        assertEquals(View.VISIBLE, textBalance.visibility)

        selectNothing(spinner)

        assertNull(sut.account)
        assertNull(lastNotified)
        assertEquals(View.GONE, textBalance.visibility)
    }

    // --- 5. Race guard: stale result must not overwrite a newer selection ---

    @Test
    fun selectingAccount_staleBalanceResultDoesNotOverwriteNewerSelection() {
        val spinner = Spinner(context)
        val textBalance = TextView(context)
        val results = mapOf(
            accountNoInstrument.id to Pair(111L, 111L),
            accountUsd.id to Pair(222L, 222L)
        )
        var firstCallHandled = false
        lateinit var sut: AccountSideController
        sut = controller(
            spinner = spinner,
            textBalance = textBalance,
            getBalance = { id ->
                if (id == accountNoInstrument.id && !firstCallHandled) {
                    firstCallHandled = true
                    // Simulate a second, newer selection completing (synchronously, since
                    // runInBackground/runOnUiThread are synchronous in tests) before this
                    // first (now-stale) call's own result is applied.
                    select(spinner, 2)
                }
                results[id]
            }
        )
        sut.populate(listOf(accountNoInstrument, accountUsd))

        select(spinner, 1) // selects accountNoInstrument, triggers the nested newer selection

        assertEquals(accountUsd, sut.account)
        assertEquals(222L, sut.balance)
        assertEquals(222L, sut.instrumentBalance)
        assertEquals(
            context.getString(
                R.string.label_balance_ar_instrument,
                TransactionDisplay.formatAmount(222L),
                TransactionDisplay.formatInstrumentAmount(222L, usd)
            ),
            textBalance.text.toString()
        )
    }

    // --- 6. hideBalance() ---

    @Test
    fun hideBalance_setsVisibilityGoneDirectly() {
        val textBalance = TextView(context)
        textBalance.visibility = View.VISIBLE
        val sut = controller(textBalance = textBalance)

        sut.hideBalance()

        assertEquals(View.GONE, textBalance.visibility)
    }

    // --- 7. clearSelection() ---

    @Test
    fun clearSelection_resetsAccountAndBalanceButKeepsAccountsAndAdapter() {
        val spinner = Spinner(context)
        val textBalance = TextView(context)
        val sut = controller(
            spinner = spinner,
            textBalance = textBalance,
            getBalance = { Pair(1000L, 1000L) }
        )
        val accounts = listOf(accountNoInstrument)
        sut.populate(accounts)
        select(spinner, 1)
        val adapterBeforeClear = spinner.adapter

        sut.clearSelection()

        assertNull(sut.account)
        assertEquals(0L, sut.balance)
        assertEquals(0L, sut.instrumentBalance)
        assertEquals(View.GONE, textBalance.visibility)
        assertEquals(accounts, sut.accounts)
        assertSame(adapterBeforeClear, spinner.adapter)
    }

    // --- 8. isDebit ---

    @Test
    fun isDebit_exposedAsConstructedTrue() {
        val sut = controller(isDebit = true)
        assertTrue(sut.isDebit)
    }

    @Test
    fun isDebit_exposedAsConstructedFalse() {
        val sut = controller(isDebit = false)
        assertFalse(sut.isDebit)
    }
}
