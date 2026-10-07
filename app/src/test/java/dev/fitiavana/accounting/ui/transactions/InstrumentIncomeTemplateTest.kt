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
class InstrumentIncomeTemplateTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private lateinit var assetSpinner: Spinner
    private lateinit var revenueSpinner: Spinner
    private lateinit var controller: InstrumentIncomeController

    private val usd = Instrument(code = "USD", note = "", type = "fiat", decimalPlaces = 2)
    private val usdWallet = Account(id = "usd_wallet", name = "USD Wallet", type = "asset", instrumentCode = "USD")
    private val sales = Account(id = "sales", name = "Sales", type = "revenue")

    @Before
    fun setUp() {
        assetSpinner = Spinner(context)
        revenueSpinner = Spinner(context)
        controller = InstrumentIncomeController(
            context = context,
            viewModel = mock(),
            instrumentsMap = mapOf("USD" to usd),
            assetSpinner = assetSpinner,
            assetTextBalance = TextView(context),
            assetTextNewBalance = TextView(context),
            revenueSpinner = revenueSpinner,
            revenueTextBalance = TextView(context),
            revenueTextNewBalance = TextView(context),
            textAmountCode = TextView(context),
            editIncomeAmount = EditText(context),
            textAmountBase = TextView(context),
            onChanged = {},
            runInBackground = { it() },
            runOnUiThread = { it() }
        ).also { it.populateSpinners(listOf(usdWallet, sales)) }
    }

    private fun template(vararg slots: Pair<String, String>) = TemplateWithEntries(
        TransactionTemplate("t", "Interest", TemplateModes.INSTRUMENT_INCOME, 1L),
        slots.mapIndexed { i, (slot, account) -> TemplateEntry("e$i", "t", account, slot, i) }
    )

    @Test
    fun `templateSlots is null until both accounts are chosen`() {
        assertNull(controller.templateSlots())

        assetSpinner.setSelection(1)
        assertNull(controller.templateSlots())
    }

    @Test
    fun `templateSlots returns the chosen ASSET and REVENUE accounts`() {
        assetSpinner.setSelection(1)
        revenueSpinner.setSelection(1)

        assertEquals(
            listOf(TemplateSlot(TemplateSlots.ASSET, "usd_wallet"), TemplateSlot(TemplateSlots.REVENUE, "sales")),
            controller.templateSlots()
        )
    }

    @Test
    fun `applyTemplate selects both accounts`() {
        val applied = controller.applyTemplate(
            template(TemplateSlots.ASSET to "usd_wallet", TemplateSlots.REVENUE to "sales")
        )

        assertTrue(applied)
        assertEquals(1, assetSpinner.selectedItemPosition)
        assertEquals(1, revenueSpinner.selectedItemPosition)
    }

    @Test
    fun `applyTemplate is false when an account is not offered any more`() {
        val applied = controller.applyTemplate(
            template(TemplateSlots.ASSET to "usd_wallet", TemplateSlots.REVENUE to "gone")
        )

        assertFalse(applied)
    }
}
