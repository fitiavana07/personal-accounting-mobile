package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
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
class SimpleTransferTemplateTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private lateinit var fromSpinner: Spinner
    private lateinit var toSpinner: Spinner
    private lateinit var controller: SimpleTransferController

    private val cash = Account(id = "cash", name = "Cash", type = "asset")
    private val bank = Account(id = "bank", name = "Bank", type = "asset")

    @Before
    fun setUp() {
        fromSpinner = Spinner(context)
        toSpinner = Spinner(context)
        controller = SimpleTransferController(
            context = context,
            viewModel = mock(),
            fromSpinner = fromSpinner,
            fromTextBalance = TextView(context),
            fromTextNewBalance = TextView(context),
            fromTextZeroBalanceError = TextView(context),
            toSpinner = toSpinner,
            toTextBalance = TextView(context),
            toTextNewBalance = TextView(context),
            editTransferAmount = EditText(context),
            buttonAll = Button(context),
            onChanged = {},
            runInBackground = { it() },
            runOnUiThread = { it() }
        ).also { it.populateSpinners(listOf(cash, bank)) }
    }

    private fun template(vararg slots: Pair<String, String>) = TemplateWithEntries(
        TransactionTemplate("t", "Withdraw", TemplateModes.SIMPLE_TRANSFER, 1L),
        slots.mapIndexed { i, (slot, account) -> TemplateEntry("e$i", "t", account, slot, i) }
    )

    @Test
    fun `templateSlots is null until both accounts are chosen`() {
        assertNull(controller.templateSlots())

        fromSpinner.setSelection(1)
        assertNull(controller.templateSlots())
    }

    @Test
    fun `templateSlots returns FROM then TO with the chosen accounts`() {
        fromSpinner.setSelection(2) // bank
        toSpinner.setSelection(1) // cash

        assertEquals(
            listOf(TemplateSlot(TemplateSlots.FROM, "bank"), TemplateSlot(TemplateSlots.TO, "cash")),
            controller.templateSlots()
        )
    }

    @Test
    fun `applyTemplate selects both accounts`() {
        val applied = controller.applyTemplate(template(TemplateSlots.FROM to "bank", TemplateSlots.TO to "cash"))

        assertTrue(applied)
        assertEquals(2, fromSpinner.selectedItemPosition)
        assertEquals(1, toSpinner.selectedItemPosition)
    }

    @Test
    fun `applyTemplate reports false and selects nothing when an account is gone`() {
        val applied = controller.applyTemplate(template(TemplateSlots.FROM to "bank", TemplateSlots.TO to "deleted"))

        assertFalse(applied)
        assertEquals(0, fromSpinner.selectedItemPosition)
        assertEquals(0, toSpinner.selectedItemPosition)
    }
}
