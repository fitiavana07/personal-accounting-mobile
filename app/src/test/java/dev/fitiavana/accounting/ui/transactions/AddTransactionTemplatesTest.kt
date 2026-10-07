package dev.fitiavana.accounting.ui.transactions

import android.app.Dialog
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.templates.TemplateModes
import dev.fitiavana.accounting.features.templates.TemplateSlot
import dev.fitiavana.accounting.features.templates.TemplateSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AddTransactionTemplatesTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val container get() = AppContainer.getInstance(context)

    @Before
    fun setUp() {
        onBackground {
            val dao = AppDatabase.getInstance(context).templateDao()
            dao.deleteAllEntries()
            dao.deleteAllTemplates()
            container.accountRepository.insert(Account(id = "tpl_cash", name = "Tpl Cash", type = "asset"))
            container.accountRepository.insert(Account(id = "tpl_bank", name = "Tpl Bank", type = "asset"))
            container.accountRepository.insert(Account(id = "tpl_food", name = "Tpl Food", type = "expense"))
        }
    }

    private fun onBackground(block: () -> Unit) {
        Thread {
            try {
                block()
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // seed rows from an earlier test sharing the singleton database
            }
        }.apply { start(); join() }
    }

    private fun saveTemplate(name: String, mode: String, vararg slots: Pair<String, String>) =
        onBackground {
            container.templateRepository.save(name, mode, slots.map { TemplateSlot(it.first, it.second) })
        }

    private fun launch(): ActivityController<AddTransactionActivity> =
        Robolectric.buildActivity(AddTransactionActivity::class.java).setup()

    private fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms")
    }

    private fun AddTransactionActivity.templateList() = findViewById<LinearLayout>(R.id.list_templates)
    private fun AddTransactionActivity.templatesSection() = findViewById<View>(R.id.section_templates)

    /** Spinner position of the account called [name], found from its adapter so it does not depend on other rows. */
    private fun positionOf(spinner: Spinner, name: String): Int =
        (0 until spinner.adapter.count).first { spinner.adapter.getItem(it) == name }

    private fun selectByName(spinner: Spinner, name: String) = spinner.setSelection(positionOf(spinner, name))

    private fun selectedName(spinner: Spinner): String = spinner.selectedItem.toString()

    private fun latestDialog(): AlertDialog {
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    // --- list ---

    @Test
    fun `the templates section is hidden when there are no templates`() {
        val activity = launch().get()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, activity.templatesSection().visibility)
    }

    @Test
    fun `saved templates are listed below the mode cards, newest first`() {
        saveTemplate("Older", TemplateModes.SIMPLE_TRANSFER, TemplateSlots.FROM to "tpl_bank", TemplateSlots.TO to "tpl_cash")
        Thread.sleep(5)
        saveTemplate("Newer", TemplateModes.SIMPLE_TRANSFER, TemplateSlots.FROM to "tpl_cash", TemplateSlots.TO to "tpl_bank")
        val activity = launch().get()

        waitUntil { activity.templateList().childCount == 2 }

        assertEquals(View.VISIBLE, activity.templatesSection().visibility)
        val names = (0 until 2).map {
            activity.templateList().getChildAt(it).findViewById<TextView>(R.id.text_template_name).text.toString()
        }
        assertEquals(listOf("Newer", "Older"), names)
    }

    // --- using a template ---

    @Test
    fun `tapping a simple transfer template opens that mode with both accounts selected`() {
        saveTemplate("Withdraw", TemplateModes.SIMPLE_TRANSFER, TemplateSlots.FROM to "tpl_bank", TemplateSlots.TO to "tpl_cash")
        val activity = launch().get()
        waitUntil { activity.templateList().childCount == 1 }

        activity.templateList().getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.step_mode_selection).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.step_transaction_form).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mode_simple_transfer).visibility)
        assertEquals("Tpl Bank", selectedName(activity.findViewById(R.id.spinner_transfer_from)))
        assertEquals("Tpl Cash", selectedName(activity.findViewById(R.id.spinner_transfer_to)))
    }

    @Test
    fun `tapping a classic template adds the rows it needs and selects their accounts`() {
        saveTemplate(
            "Groceries", TemplateModes.CLASSIC,
            TemplateSlots.ROW to "tpl_food", TemplateSlots.ROW to "tpl_cash", TemplateSlots.ROW to "tpl_bank"
        )
        val activity = launch().get()
        waitUntil { activity.templateList().childCount == 1 }

        activity.templateList().getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val rows = activity.findViewById<LinearLayout>(R.id.entries_container)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mode_classic).visibility)
        assertEquals(3, rows.childCount)
        assertEquals(
            listOf("Tpl Food", "Tpl Cash", "Tpl Bank"),
            (0 until 3).map { selectedName(rows.getChildAt(it).findViewById(R.id.spinner_account)) }
        )
    }

    @Test
    fun `a template whose account is no longer available opens the form with a warning`() {
        saveTemplate("Odd", TemplateModes.INSTRUMENT_INCOME, TemplateSlots.ASSET to "tpl_cash", TemplateSlots.REVENUE to "tpl_cash")
        val activity = launch().get()
        waitUntil { activity.templateList().childCount == 1 }

        activity.templateList().getChildAt(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.step_transaction_form).visibility)
        assertEquals(activity.getString(R.string.template_accounts_unavailable), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `launching with a template id goes straight to its form`() {
        saveTemplate("Withdraw", TemplateModes.SIMPLE_TRANSFER, TemplateSlots.FROM to "tpl_bank", TemplateSlots.TO to "tpl_cash")
        var id: String? = null
        onBackground { id = container.templateRepository.getAll().first().template.id }

        val activity = Robolectric.buildActivity(
            AddTransactionActivity::class.java,
            AddTransactionActivity.intent(context, id)
        ).setup().get()
        waitUntil { activity.findViewById<View>(R.id.step_transaction_form).visibility == View.VISIBLE }

        assertEquals("Tpl Bank", selectedName(activity.findViewById(R.id.spinner_transfer_from)))
    }

    // --- deleting ---

    @Test
    fun `deleting a template asks for confirmation and then removes it from the list`() {
        saveTemplate("Withdraw", TemplateModes.SIMPLE_TRANSFER, TemplateSlots.FROM to "tpl_bank", TemplateSlots.TO to "tpl_cash")
        val activity = launch().get()
        waitUntil { activity.templateList().childCount == 1 }

        activity.templateList().getChildAt(0).findViewById<View>(R.id.btn_delete_template).performClick()
        latestDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()

        waitUntil { activity.templateList().childCount == 0 }
        assertEquals(View.GONE, activity.templatesSection().visibility)
        var remaining = -1
        onBackground { remaining = container.templateRepository.getAll().size }
        assertEquals(0, remaining)
    }

    @Test
    fun `cancelling the delete confirmation keeps the template`() {
        saveTemplate("Withdraw", TemplateModes.SIMPLE_TRANSFER, TemplateSlots.FROM to "tpl_bank", TemplateSlots.TO to "tpl_cash")
        val activity = launch().get()
        waitUntil { activity.templateList().childCount == 1 }

        activity.templateList().getChildAt(0).findViewById<View>(R.id.btn_delete_template).performClick()
        latestDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, activity.templateList().childCount)
    }

    // --- saving ---

    private fun AddTransactionActivity.openSimpleTransferForm() {
        findViewById<View>(R.id.mode_option_simple_transfer).performClick()
        waitUntil { findViewById<Spinner>(R.id.spinner_transfer_from).adapter != null }
    }

    @Test
    fun `save as template needs accounts to be chosen first`() {
        val activity = launch().get()
        activity.openSimpleTransferForm()

        activity.findViewById<View>(R.id.btn_save_template).performClick()

        assertEquals(activity.getString(R.string.error_template_accounts_required), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `save as template asks for a name and stores the mode and accounts without amounts`() {
        val activity = launch().get()
        activity.openSimpleTransferForm()
        selectByName(activity.findViewById(R.id.spinner_transfer_from), "Tpl Bank")
        selectByName(activity.findViewById(R.id.spinner_transfer_to), "Tpl Cash")

        activity.findViewById<View>(R.id.btn_save_template).performClick()
        val dialog = latestDialog()
        dialog.findViewById<EditText>(R.id.edit_template_name)!!.setText("  Withdraw cash ")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()

        waitUntil { ShadowToast.getTextOfLatestToast() == activity.getString(R.string.template_saved) }
        var saved: List<Triple<String, String, List<Pair<String, String>>>> = emptyList()
        onBackground {
            saved = container.templateRepository.getAll().map { t ->
                Triple(t.template.name, t.template.mode, t.entries.map { it.slot to it.accountId })
            }
        }
        assertEquals(
            listOf(
                Triple(
                    "Withdraw cash", TemplateModes.SIMPLE_TRANSFER,
                    listOf(TemplateSlots.FROM to "tpl_bank", TemplateSlots.TO to "tpl_cash")
                )
            ),
            saved
        )
    }

    @Test
    fun `a saved template shows up in the list without reopening the screen`() {
        val activity = launch().get()
        activity.openSimpleTransferForm()
        selectByName(activity.findViewById(R.id.spinner_transfer_from), "Tpl Bank")
        selectByName(activity.findViewById(R.id.spinner_transfer_to), "Tpl Cash")
        activity.findViewById<View>(R.id.btn_save_template).performClick()
        val dialog = latestDialog()
        dialog.findViewById<EditText>(R.id.edit_template_name)!!.setText("Withdraw")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()

        waitUntil { activity.templateList().childCount == 1 }

        assertNotNull(activity.templateList().getChildAt(0))
    }

    @Test
    fun `a blank template name is refused`() {
        val activity = launch().get()
        activity.openSimpleTransferForm()
        selectByName(activity.findViewById(R.id.spinner_transfer_from), "Tpl Bank")
        selectByName(activity.findViewById(R.id.spinner_transfer_to), "Tpl Cash")
        activity.findViewById<View>(R.id.btn_save_template).performClick()
        val dialog = latestDialog()
        dialog.findViewById<EditText>(R.id.edit_template_name)!!.setText("   ")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(activity.getString(R.string.error_template_name_required), ShadowToast.getTextOfLatestToast())
        assertEquals(0, activity.templateList().childCount)
    }

    @Test
    fun `a classic template needs at least two different accounts`() {
        val activity = launch().get()
        activity.findViewById<View>(R.id.mode_option_classic).performClick()
        val rows = activity.findViewById<LinearLayout>(R.id.entries_container)
        waitUntil { rows.childCount == 2 }
        selectByName(rows.getChildAt(0).findViewById(R.id.spinner_account), "Tpl Cash")
        selectByName(rows.getChildAt(1).findViewById(R.id.spinner_account), "Tpl Cash")

        activity.findViewById<View>(R.id.btn_save_template).performClick()

        assertEquals(activity.getString(R.string.error_template_accounts_required), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a classic template saves its rows in order`() {
        val activity = launch().get()
        activity.findViewById<View>(R.id.mode_option_classic).performClick()
        val rows = activity.findViewById<LinearLayout>(R.id.entries_container)
        waitUntil { rows.childCount == 2 }
        selectByName(rows.getChildAt(0).findViewById(R.id.spinner_account), "Tpl Food")
        selectByName(rows.getChildAt(1).findViewById(R.id.spinner_account), "Tpl Cash")

        activity.findViewById<View>(R.id.btn_save_template).performClick()
        val dialog = latestDialog()
        dialog.findViewById<EditText>(R.id.edit_template_name)!!.setText("Groceries")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()

        waitUntil { activity.templateList().childCount == 1 }
        var rowAccounts: List<String> = emptyList()
        onBackground { rowAccounts = container.templateRepository.getAll().single().rowAccountIds() }
        assertEquals(listOf("tpl_food", "tpl_cash"), rowAccounts)
        assertTrue(activity.findViewById<View>(R.id.btn_save_template).isEnabled)
    }
}
