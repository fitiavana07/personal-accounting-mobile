package dev.fitiavana.accounting.ui.transactions

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests for the toggle + amount + expense account group shared by the swap and transfer fees. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class FeeSectionTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )
    private lateinit var toggle: SwitchCompat
    private lateinit var group: View
    private lateinit var amount: EditText
    private lateinit var spinner: Spinner
    private var toggled = 0
    private var chosen = 0

    private val fuel = Account(id = "fuel", name = "Fuel", type = "expense")
    private val rent = Account(id = "rent", name = "Rent", type = "expense")

    @Before
    fun setUp() {
        toggle = SwitchCompat(context)
        group = View(context)
        amount = EditText(context)
        spinner = Spinner(context)
        toggled = 0
        chosen = 0
    }

    private fun section() = FeeSection(
        context = context,
        toggle = toggle,
        group = group,
        amountField = amount,
        accountSpinner = spinner,
        onToggled = { toggled++ },
        onAccountChosen = { chosen++ }
    ).also { it.populate(listOf(fuel, rent)) }

    private fun names() = (1 until spinner.adapter.count).map { spinner.adapter.getItem(it).toString() }

    @Test
    fun `starts off with its group hidden`() {
        val section = section()

        assertFalse(section.isOn)
        assertEquals(View.GONE, group.visibility)
    }

    @Test
    fun `toggling on shows the group and notifies`() {
        section()

        toggle.isChecked = true

        assertEquals(View.VISIBLE, group.visibility)
        assertEquals(1, toggled)
    }

    @Test
    fun `toggling off hides the group and clears the amount and account`() {
        section()
        toggle.isChecked = true
        amount.setText("5")
        spinner.setSelection(2)

        toggle.isChecked = false

        assertEquals(View.GONE, group.visibility)
        assertEquals("", amount.text.toString())
        assertEquals(0, spinner.selectedItemPosition)
    }

    @Test
    fun `isOn can be set from outside`() {
        val section = section()

        section.isOn = true

        assertTrue(toggle.isChecked)
        assertEquals(View.VISIBLE, group.visibility)
    }

    @Test
    fun `populate offers select account then the given accounts`() {
        val section = section()

        assertEquals(listOf("Fuel", "Rent"), names())
        assertNull(section.selectedAccountId())
    }

    @Test
    fun `selectedAccountId maps the spinner position to the account`() {
        val section = section()

        spinner.setSelection(2)

        assertEquals("rent", section.selectedAccountId())
    }

    @Test
    fun `populate keeps a selection that is still offered and resets one that is not`() {
        val section = section()
        spinner.setSelection(2) // Rent

        section.populate(listOf(rent))
        assertEquals("rent", section.selectedAccountId())

        section.populate(listOf(fuel))
        assertNull(section.selectedAccountId())
        assertEquals(0, spinner.selectedItemPosition)
    }

    @Test
    fun `choosing an account notifies`() {
        section()
        val before = chosen

        spinner.setSelection(1)

        assertTrue(chosen > before)
    }
}
