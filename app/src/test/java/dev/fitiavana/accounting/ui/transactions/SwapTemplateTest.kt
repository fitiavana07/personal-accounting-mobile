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

    private val usd = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)
    private val btc = Instrument(code = "BTC", note = "", type = "crypto", decimalPlaces = 8)
    private val usdWallet = Account(id = "usd", name = "USD Wallet", type = "asset", instrumentCode = "USD")
    private val btcViaUsd = Account(
        id = "btc", name = "BTC", type = "asset", instrumentCode = "BTC", intermediaryInstrumentCode = "USD"
    )
    private val accounts = listOf(usdWallet, btcViaUsd)

    @Before
    fun setUp() {
        fromSpinner = Spinner(context)
        toSpinner = Spinner(context)
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
            switchFee = androidx.appcompat.widget.SwitchCompat(context),
            feeGroup = android.view.View(context),
            feeAmountCode = TextView(context),
            editFee = EditText(context),
            feeSpinner = Spinner(context),
            textFeePreview = TextView(context),
            textFeeReceived = TextView(context),
            onChanged = {},
            runInBackground = { it() },
            runOnUiThread = { it() }
        ).also { it.populateSpinners(accounts, accounts.associate { a -> a.id to 1L }) }
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
}
