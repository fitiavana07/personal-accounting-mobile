package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.templates.TemplateEntry
import dev.fitiavana.accounting.features.templates.TemplateModes
import dev.fitiavana.accounting.features.templates.TemplateSlot
import dev.fitiavana.accounting.features.templates.TemplateSlots
import dev.fitiavana.accounting.features.templates.TemplateWithEntries
import dev.fitiavana.accounting.features.templates.TransactionTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SwapTemplateTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private lateinit var fromSpinner: Spinner
    private lateinit var toSpinner: Spinner
    private lateinit var controller: SwapController
    private lateinit var secondaryFeeSection: android.view.View
    private lateinit var switchSecondaryFee: androidx.appcompat.widget.SwitchCompat
    private lateinit var secondaryFeeGroup: android.view.View
    private lateinit var switchFee: androidx.appcompat.widget.SwitchCompat
    private lateinit var feeSpinner: Spinner
    private lateinit var secondaryFeeSpinner: Spinner

    private val usd = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)
    private val btc = Instrument(code = "BTC", note = "", type = "crypto", decimalPlaces = 8)
    private val cash = Account(id = "cash", name = "Cash", type = "asset")
    private val usdWallet = Account(id = "usd", name = "USD Wallet", type = "asset", instrumentCode = "USD")
    private val btcViaUsd = Account(
        id = "btc", name = "BTC", type = "asset", instrumentCode = "BTC", intermediaryInstrumentCode = "USD"
    )
    private val btcWallet = Account(id = "btcw", name = "BTC Wallet", type = "asset", instrumentCode = "BTC")
    private val accounts = listOf(usdWallet, btcViaUsd)
    private val feeExpense = Account(id = "fee", name = "Swap fees", type = "expense")
    private val otherExpense = Account(id = "fee2", name = "Other fees", type = "expense")

    @Before
    fun setUp() {
        fromSpinner = Spinner(context)
        toSpinner = Spinner(context)
        secondaryFeeSection = android.view.View(context)
        switchSecondaryFee = androidx.appcompat.widget.SwitchCompat(context)
        secondaryFeeGroup = android.view.View(context)
        switchFee = androidx.appcompat.widget.SwitchCompat(context)
        feeSpinner = Spinner(context)
        secondaryFeeSpinner = Spinner(context)
        controller = SwapController(
            context = context,
            viewModel = mock(),
            instrumentsMap = mapOf("USD" to usd, "BTC" to btc),
            fromSpinner = fromSpinner,
            fromTextBalance = TextView(context),
            fromTextNewBalance = TextView(context),
            fromTextZeroBalanceError = TextView(context),
            toSpinner = toSpinner,
            toTextBalance = TextView(context),
            toTextNewBalance = TextView(context),
            fromAmountCode = TextView(context),
            editFromAmount = EditText(context),
            buttonAll = android.widget.Button(context),
            toAmountCode = TextView(context),
            editToAmount = EditText(context),
            textAmountBase = TextView(context),
            textRate = TextView(context),
            switchFee = switchFee,
            feeGroup = android.view.View(context),
            feeAmountCode = TextView(context),
            editFee = EditText(context),
            feeSpinner = feeSpinner,
            textFeePreview = TextView(context),
            textFeeReceived = TextView(context),
            secondaryFeeSection = secondaryFeeSection,
            switchSecondaryFee = switchSecondaryFee,
            secondaryFeeGroup = secondaryFeeGroup,
            secondaryFeeAmountCode = TextView(context),
            editSecondaryFee = EditText(context),
            secondaryFeeSpinner = secondaryFeeSpinner,
            onChanged = {},
            runInBackground = { it() },
            runOnUiThread = { it() }
        ).also { it.populateSpinners(accounts + listOf(feeExpense, otherExpense), accounts.associate { a -> a.id to 1L }) }
    }

    private fun template(vararg slots: Pair<String, String>) = TemplateWithEntries(
        TransactionTemplate("t", "Buy BTC", TemplateModes.SWAP, 1L),
        slots.mapIndexed { i, (slot, account) -> TemplateEntry("e$i", "t", account, slot, i) }
    )

    @Test
    fun `swap template is complete only with FROM and TO`() {
        assertTrue(template(TemplateSlots.FROM to "usd", TemplateSlots.TO to "btc").isComplete())
        assertFalse(template(TemplateSlots.FROM to "usd").isComplete())
    }

    @Test
    fun `templateSlots is null until both accounts are chosen`() {
        assertNull(controller.templateSlots())

        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)

        assertEquals(
            listOf(TemplateSlot(TemplateSlots.FROM, "usd"), TemplateSlot(TemplateSlots.TO, "btc")),
            controller.templateSlots()
        )
    }

    @Test
    fun `applyTemplate selects FROM then TO`() {
        assertTrue(controller.applyTemplate(template(TemplateSlots.FROM to "usd", TemplateSlots.TO to "btc")))

        assertEquals("usd", controller.templateSlots()?.first()?.accountId)
        assertEquals("btc", controller.templateSlots()?.last()?.accountId)
    }

    @Test
    fun `applyTemplate is false when TO does not fit FROM or an account is gone`() {
        assertFalse(controller.applyTemplate(template(TemplateSlots.FROM to "usd", TemplateSlots.TO to "usd")))
        assertFalse(controller.applyTemplate(template(TemplateSlots.FROM to "gone", TemplateSlots.TO to "btc")))
        assertNull(controller.templateSlots())
    }

    /** Reloads the controller's accounts so a funded base account and a no-intermediary target are offered. */
    private fun withBaseAccounts() = controller.populateSpinners(
        accounts + listOf(cash, btcWallet, feeExpense, otherExpense),
        mapOf("usd" to 1L, "cash" to 1L)
    )

    @Test
    fun `applyTemplate with a base FROM selects both accounts and turns the fees on`() {
        withBaseAccounts()

        assertTrue(controller.applyTemplate(template(TemplateSlots.FROM to "cash", TemplateSlots.TO to "btcw")))

        assertEquals("cash", controller.templateSlots()?.first()?.accountId)
        assertEquals("btcw", controller.templateSlots()?.last()?.accountId)
        assertEquals(android.view.View.VISIBLE, secondaryFeeSection.visibility)
        assertTrue(switchSecondaryFee.isChecked)
        assertEquals(android.view.View.VISIBLE, secondaryFeeGroup.visibility)
    }

    @Test
    fun `applyTemplate with a base FROM is false when TO needs an intermediary`() {
        withBaseAccounts()

        assertFalse(controller.applyTemplate(template(TemplateSlots.FROM to "cash", TemplateSlots.TO to "btc")))
        assertNull(controller.templateSlots())
    }

    @Test
    fun `applyTemplate with an instrument FROM after a base one hides the secondary fee`() {
        withBaseAccounts()
        controller.applyTemplate(template(TemplateSlots.FROM to "cash", TemplateSlots.TO to "btcw"))

        assertTrue(controller.applyTemplate(template(TemplateSlots.FROM to "usd", TemplateSlots.TO to "btc")))

        assertEquals(android.view.View.GONE, secondaryFeeSection.visibility)
        assertFalse(switchSecondaryFee.isChecked)
    }

    @Test
    fun `applyTemplate with a base FROM adapts the form before the deferred FROM listener fires`() {
        val activity = org.robolectric.Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java)
            .create().start().resume().get()
        activity.setTheme(R.style.Theme_Accounting)
        val root = android.widget.LinearLayout(activity)
        root.visibility = android.view.View.GONE
        val fromS = Spinner(activity)
        val toS = Spinner(activity)
        listOf(fromS, toS).forEach { root.addView(it) }
        activity.setContentView(root)
        val section = android.view.View(activity)
        val secondarySwitch = androidx.appcompat.widget.SwitchCompat(activity)
        val c = SwapController(
            context = activity, viewModel = mock(),
            instrumentsMap = mapOf("USD" to usd, "BTC" to btc),
            fromSpinner = fromS, fromTextBalance = TextView(activity), fromTextNewBalance = TextView(activity),
            fromTextZeroBalanceError = TextView(activity), toSpinner = toS, toTextBalance = TextView(activity),
            toTextNewBalance = TextView(activity), fromAmountCode = TextView(activity),
            editFromAmount = EditText(activity), buttonAll = android.widget.Button(activity),
            toAmountCode = TextView(activity), editToAmount = EditText(activity), textAmountBase = TextView(activity),
            textRate = TextView(activity), switchFee = androidx.appcompat.widget.SwitchCompat(activity),
            feeGroup = android.view.View(activity), feeAmountCode = TextView(activity), editFee = EditText(activity),
            feeSpinner = Spinner(activity), textFeePreview = TextView(activity), textFeeReceived = TextView(activity),
            secondaryFeeSection = section, switchSecondaryFee = secondarySwitch,
            secondaryFeeGroup = android.view.View(activity), secondaryFeeAmountCode = TextView(activity),
            editSecondaryFee = EditText(activity), secondaryFeeSpinner = Spinner(activity),
            onChanged = {}, runInBackground = { it() }, runOnUiThread = { it() }
        )
        c.populateSpinners(accounts + listOf(cash, btcWallet), mapOf("usd" to 1L, "cash" to 1L))

        assertTrue(c.applyTemplate(template(TemplateSlots.FROM to "cash", TemplateSlots.TO to "btcw")))

        assertEquals(android.view.View.VISIBLE, section.visibility)
        assertTrue(secondarySwitch.isChecked)
    }

    @Test
    fun `applyTemplate on spinners attached to a hidden window still shows from balance and unit codes once shown`() {
        val activity = org.robolectric.Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java)
            .create().start().resume().get()
        activity.setTheme(R.style.Theme_Accounting)
        val root = android.widget.LinearLayout(activity)
        root.visibility = android.view.View.GONE
        val fromBalance = TextView(activity)
        val fromCode = TextView(activity)
        val fromS = Spinner(activity)
        val toS = Spinner(activity)
        listOf(fromS, fromBalance, fromCode, toS).forEach { root.addView(it) }
        activity.setContentView(root)
        val vm = mock<AddTransactionViewModel>()
        org.mockito.kotlin.whenever(vm.getBalance("usd")).thenReturn(
            dev.fitiavana.accounting.features.balances.AccountBalance("usd", 100L, 10L, 0L, 0L, 0L)
        )
        val c = SwapController(
            context = activity, viewModel = vm,
            instrumentsMap = mapOf("USD" to usd, "BTC" to btc),
            fromSpinner = fromS, fromTextBalance = fromBalance, fromTextNewBalance = TextView(activity), fromTextZeroBalanceError = TextView(activity),
            toSpinner = toS, toTextBalance = TextView(activity), toTextNewBalance = TextView(activity), fromAmountCode = fromCode,
            editFromAmount = EditText(activity), buttonAll = android.widget.Button(activity), toAmountCode = TextView(activity),
            editToAmount = EditText(activity), textAmountBase = TextView(activity), textRate = TextView(activity),
            switchFee = androidx.appcompat.widget.SwitchCompat(activity), feeGroup = android.view.View(activity),
            feeAmountCode = TextView(activity), editFee = EditText(activity), feeSpinner = Spinner(activity),
            textFeePreview = TextView(activity), textFeeReceived = TextView(activity),
            secondaryFeeSection = android.view.View(activity),
            switchSecondaryFee = androidx.appcompat.widget.SwitchCompat(activity),
            secondaryFeeGroup = android.view.View(activity), secondaryFeeAmountCode = TextView(activity),
            editSecondaryFee = EditText(activity), secondaryFeeSpinner = Spinner(activity),
            onChanged = {}, runInBackground = { it() }, runOnUiThread = { it() }
        )
        c.populateSpinners(accounts, accounts.associate { a -> a.id to 1L })

        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        root.visibility = android.view.View.VISIBLE
        looper.idle()
        fromS.setSelection(1)
        toS.setSelection(1)
        looper.idle()
        c.clear()
        root.visibility = android.view.View.GONE
        looper.idle()
        c.applyTemplate(template(TemplateSlots.FROM to "usd", TemplateSlots.TO to "btc"))
        root.visibility = android.view.View.VISIBLE
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("USD", fromCode.text.toString())
        assertEquals(android.view.View.VISIBLE, fromBalance.visibility)
    }

    private fun spinnerSelectedName(spinner: Spinner) = spinner.selectedItem?.toString()

    @Test
    fun `templateSlots saves the swap fee account while the fee is on`() {
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        switchFee.isChecked = true
        feeSpinner.setSelection(1)

        assertEquals(
            listOf(
                TemplateSlot(TemplateSlots.FROM, "usd"),
                TemplateSlot(TemplateSlots.TO, "btc"),
                TemplateSlot(TemplateSlots.FEE, "fee")
            ),
            controller.templateSlots()
        )
    }

    @Test
    fun `templateSlots leaves the fee out when it is off or has no account`() {
        fromSpinner.setSelection(1)
        toSpinner.setSelection(1)
        assertEquals(2, controller.templateSlots()?.size)

        switchFee.isChecked = true
        assertEquals(2, controller.templateSlots()?.size)
    }

    @Test
    fun `templateSlots saves both fee accounts for a base FROM`() {
        withBaseAccounts()
        fromSpinner.setSelection(indexOfName(fromSpinner, cash.name))
        toSpinner.setSelection(indexOfName(toSpinner, btcWallet.name))
        feeSpinner.setSelection(1)
        secondaryFeeSpinner.setSelection(indexOfName(secondaryFeeSpinner, otherExpense.name))

        assertEquals(
            listOf(
                TemplateSlot(TemplateSlots.FROM, "cash"),
                TemplateSlot(TemplateSlots.TO, "btcw"),
                TemplateSlot(TemplateSlots.FEE, "fee"),
                TemplateSlot(TemplateSlots.SECONDARY_FEE, "fee2")
            ),
            controller.templateSlots()
        )
    }

    private fun indexOfName(spinner: Spinner, name: String): Int =
        (0 until spinner.count).first { spinner.getItemAtPosition(it).toString() == name }

    @Test
    fun `applyTemplate restores the swap fee account and turns the fee on`() {
        assertTrue(
            controller.applyTemplate(
                template(TemplateSlots.FROM to "usd", TemplateSlots.TO to "btc", TemplateSlots.FEE to "fee")
            )
        )

        assertTrue(switchFee.isChecked)
        assertEquals("Swap fees", spinnerSelectedName(feeSpinner))
        assertFalse(switchSecondaryFee.isChecked)
    }

    @Test
    fun `applyTemplate keeps the fee off when the template has no fee account`() {
        controller.applyTemplate(template(TemplateSlots.FROM to "usd", TemplateSlots.TO to "btc"))

        assertFalse(switchFee.isChecked)
    }

    @Test
    fun `applyTemplate restores both fee accounts for a base FROM even though the kind change resets the fees`() {
        withBaseAccounts()

        assertTrue(
            controller.applyTemplate(
                template(
                    TemplateSlots.FROM to "cash", TemplateSlots.TO to "btcw",
                    TemplateSlots.FEE to "fee", TemplateSlots.SECONDARY_FEE to "fee2"
                )
            )
        )

        assertTrue(switchFee.isChecked)
        assertEquals("Swap fees", spinnerSelectedName(feeSpinner))
        assertTrue(switchSecondaryFee.isChecked)
        assertEquals("Other fees", spinnerSelectedName(secondaryFeeSpinner))
    }

    @Test
    fun `applyTemplate ignores a fee account that is no longer offered but still applies`() {
        assertTrue(
            controller.applyTemplate(
                template(TemplateSlots.FROM to "usd", TemplateSlots.TO to "btc", TemplateSlots.FEE to "gone")
            )
        )

        assertEquals("usd", controller.templateSlots()?.first()?.accountId)
        assertEquals(2, controller.templateSlots()?.size)
    }
}
