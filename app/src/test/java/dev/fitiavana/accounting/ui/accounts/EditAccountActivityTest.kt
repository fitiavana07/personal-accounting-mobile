package dev.fitiavana.accounting.ui.accounts

import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class EditAccountActivityTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val accountRepository get() = AppContainer.getInstance(context).accountRepository

    private fun launchNew(): ActivityController<EditAccountActivity> =
        Robolectric.buildActivity(EditAccountActivity::class.java, EditAccountActivity.addIntent(context)).setup()

    private fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms")
    }

    private fun EditAccountActivity.selectType(type: String) {
        findViewById<Spinner>(R.id.spinner_account_type).setSelection(AccountTypes.VALUES.indexOf(type))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun inBackground(block: () -> Unit) = Thread(block).apply { start(); join() }

    @Test
    fun `the APR field is shown for asset accounts`() {
        val activity = launchNew().get()

        activity.selectType(AccountTypes.ASSET)

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.label_apr).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.input_apr).visibility)
    }

    @Test
    fun `the APR field is hidden for other account types`() {
        val activity = launchNew().get()

        listOf(
            AccountTypes.LIABILITY, AccountTypes.EQUITY, AccountTypes.REVENUE, AccountTypes.EXPENSE,
            AccountTypes.DRAWING, AccountTypes.GAIN, AccountTypes.LOSS
        ).forEach { type ->
            activity.selectType(type)

            assertEquals(type, View.GONE, activity.findViewById<View>(R.id.input_apr).visibility)
            assertEquals(type, View.GONE, activity.findViewById<View>(R.id.label_apr).visibility)
        }
    }

    @Test
    fun `an existing account's APR is shown when editing`() {
        inBackground { accountRepository.insert(Account(id = "earn_edit", name = "Earn Edit", type = "asset", aprPercent = 4.5)) }
        val activity = Robolectric.buildActivity(
            EditAccountActivity::class.java, EditAccountActivity.editIntent(context, "earn_edit")
        ).setup().get()

        waitUntil { activity.findViewById<EditText>(R.id.input_account_name).text.toString() == "Earn Edit" }

        assertEquals("4.5", activity.findViewById<EditText>(R.id.input_apr).text.toString())
    }

    @Test
    fun `an account without an APR leaves the field empty when editing`() {
        inBackground { accountRepository.insert(Account(id = "plain_edit", name = "Plain Edit", type = "asset")) }
        val activity = Robolectric.buildActivity(
            EditAccountActivity::class.java, EditAccountActivity.editIntent(context, "plain_edit")
        ).setup().get()

        waitUntil { activity.findViewById<EditText>(R.id.input_account_name).text.toString() == "Plain Edit" }

        assertEquals("", activity.findViewById<EditText>(R.id.input_apr).text.toString())
    }

    @Test
    fun `saving an asset account stores the typed APR`() {
        val activity = launchNew().get()
        activity.selectType(AccountTypes.ASSET)
        activity.findViewById<EditText>(R.id.input_account_name).setText("Savings Earn Save")
        activity.findViewById<EditText>(R.id.input_apr).setText("6.25")

        activity.findViewById<View>(R.id.button_save).performClick()
        waitUntil { activity.isFinishing }

        var saved: Account? = null
        inBackground { saved = accountRepository.getAllSync().firstOrNull { it.name == "Savings Earn Save" } }
        assertEquals(6.25, saved?.aprPercent)
    }

    @Test
    fun `a comma decimal separator is accepted`() {
        val activity = launchNew().get()
        activity.selectType(AccountTypes.ASSET)
        activity.findViewById<EditText>(R.id.input_account_name).setText("Comma Earn")
        activity.findViewById<EditText>(R.id.input_apr).setText("7,5")

        activity.findViewById<View>(R.id.button_save).performClick()
        waitUntil { activity.isFinishing }

        var saved: Account? = null
        inBackground { saved = accountRepository.getAllSync().firstOrNull { it.name == "Comma Earn" } }
        assertEquals(7.5, saved?.aprPercent)
    }

    @Test
    fun `leaving the APR empty saves no APR`() {
        val activity = launchNew().get()
        activity.selectType(AccountTypes.ASSET)
        activity.findViewById<EditText>(R.id.input_account_name).setText("No Apr Account")

        activity.findViewById<View>(R.id.button_save).performClick()
        waitUntil { activity.isFinishing }

        var saved: Account? = null
        inBackground { saved = accountRepository.getAllSync().firstOrNull { it.name == "No Apr Account" } }
        assertNull(saved?.aprPercent)
    }

    @Test
    fun `an APR typed before switching to another type is not saved`() {
        val activity = launchNew().get()
        activity.selectType(AccountTypes.ASSET)
        activity.findViewById<EditText>(R.id.input_apr).setText("5")
        activity.selectType(AccountTypes.EXPENSE)
        activity.findViewById<EditText>(R.id.input_account_name).setText("Expense Not Earn")

        activity.findViewById<View>(R.id.button_save).performClick()
        waitUntil { activity.isFinishing }

        var saved: Account? = null
        inBackground { saved = accountRepository.getAllSync().firstOrNull { it.name == "Expense Not Earn" } }
        assertNull(saved?.aprPercent)
    }
}
