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
class InstrumentTransferTemplateTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private lateinit var fromSpinner: Spinner
    private lateinit var toSpinner: Spinner
    private lateinit var controller: InstrumentTransferController

    private val usd = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)
    private val eur = Instrument(code = "EUR", note = "", type = "fiat", decimalPlaces = 2)
    private val usdWallet = Account(id = "usd_wallet", name = "USD Wallet", type = "asset", instrumentCode = "USD")
    private val usdSavings = Account(id = "usd_savings", name = "USD Savings", type = "asset", instrumentCode = "USD")
    private val eurWallet = Account(id = "eur_wallet", name = "EUR Wallet", type = "asset", instrumentCode = "EUR")

    @Before
    fun setUp() {
        fromSpinner = Spinner(context)
        toSpinner = Spinner(context)
        controller = InstrumentTransferController(
            context = context,
            viewModel = mock(),
            instrumentsMap = mapOf("USD" to usd, "EUR" to eur),
            fromSpinner = fromSpinner,
            fromTextBalance = TextView(context),
            fromTextNewBalance = TextView(context),
            fromTextZeroBalanceError = TextView(context),
            toSpinner = toSpinner,
            toTextBalance = TextView(context),
            toTextNewBalance = TextView(context),
            textAmountCode = TextView(context),
            editTransferAmount = EditText(context),
            textAmountBase = TextView(context),
            onChanged = {},
            runInBackground = { it() },
            runOnUiThread = { it() }
        ).also { it.populateSpinners(listOf(usdWallet, usdSavings, eurWallet)) }
    }

    private fun template(vararg slots: Pair<String, String>) = TemplateWithEntries(
        TransactionTemplate("t", "Move USD", TemplateModes.INSTRUMENT_TRANSFER, 1L),
        slots.mapIndexed { i, (slot, account) -> TemplateEntry("e$i", "t", account, slot, i) }
    )

    @Test
    fun `templateSlots is null until both accounts are chosen`() {
        assertNull(controller.templateSlots())

        fromSpinner.setSelection(1)
        assertNull(controller.templateSlots())
    }

    @Test
    fun `templateSlots returns the chosen FROM and TO accounts`() {
        fromSpinner.setSelection(1) // usd_wallet
        toSpinner.setSelection(2) // usd_savings

        assertEquals(
            listOf(TemplateSlot(TemplateSlots.FROM, "usd_wallet"), TemplateSlot(TemplateSlots.TO, "usd_savings")),
            controller.templateSlots()
        )
    }

    @Test
    fun `applyTemplate selects FROM and then TO from the list that FROM allows`() {
        val applied = controller.applyTemplate(
            template(TemplateSlots.FROM to "usd_wallet", TemplateSlots.TO to "usd_savings")
        )

        assertTrue(applied)
        assertEquals("usd_wallet", controller.templateSlots()?.first()?.accountId)
        assertEquals("usd_savings", controller.templateSlots()?.last()?.accountId)
    }

    @Test
    fun `applyTemplate is false when TO is not allowed for FROM's instrument`() {
        val applied = controller.applyTemplate(
            template(TemplateSlots.FROM to "usd_wallet", TemplateSlots.TO to "eur_wallet")
        )

        assertFalse(applied)
        assertNull(controller.templateSlots())
    }

    @Test
    fun `applyTemplate is false when an account no longer exists`() {
        val applied = controller.applyTemplate(
            template(TemplateSlots.FROM to "gone", TemplateSlots.TO to "usd_savings")
        )

        assertFalse(applied)
    }
}
