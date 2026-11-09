package dev.fitiavana.accounting.ui.home

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class HomeShortcutsAdapterTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        R.style.Theme_Accounting
    )

    private fun inflate(
        onCex: () -> Unit = {},
        onExpenses: () -> Unit = {},
        onEarn: () -> Unit = {},
        onAlerts: () -> Unit = {},
        onMonitors: () -> Unit = {},
        onBankAccounts: () -> Unit = {}
    ) = HomeShortcutsAdapter(onCex, onExpenses, onEarn, onAlerts, onMonitors, onBankAccounts)
        .let { it.onCreateViewHolder(FrameLayout(context), 0).itemView }

    /** The first row of shortcuts: the five that share the width equally. */
    private fun mainRow() = inflate().findViewById<android.view.ViewGroup>(R.id.row_home_shortcuts_main)

    @Test
    fun bankAccountsButtonInvokesOnlyItsCallback() {
        var bank = 0
        var monitors = 0
        val view = inflate(onMonitors = { monitors++ }, onBankAccounts = { bank++ })

        view.findViewById<android.view.View>(R.id.button_bank_accounts).performClick()

        assertEquals(1, bank)
        assertEquals(0, monitors)
    }

    @Test
    fun otherButtonsDoNotTriggerBankAccounts() {
        var bank = 0
        val view = inflate(onBankAccounts = { bank++ })

        view.findViewById<android.view.View>(R.id.button_monitors).performClick()
        view.findViewById<android.view.View>(R.id.button_earn).performClick()

        assertEquals(0, bank)
    }

    @Test
    fun bankAccountsButtonIsLabelled() {
        val view = inflate()
        val label = view.findViewById<android.view.View>(R.id.button_bank_accounts)
            .findViewById<TextView>(R.id.text_shortcut_bank_accounts)

        assertEquals("Bank accounts", label.text.toString())
    }

    @Test
    fun bankAccountsButtonSitsOnASecondRow() {
        val root = inflate() as android.view.ViewGroup
        val main = root.findViewById<android.view.View>(R.id.row_home_shortcuts_main)
        val more = root.findViewById<android.view.ViewGroup>(R.id.row_home_shortcuts_more)

        assertEquals(listOf(main.id, more.id), (0 until root.childCount).map { root.getChildAt(it).id })
        assertEquals(null, main.findViewById<android.view.View>(R.id.button_bank_accounts))
        assertEquals(R.id.button_bank_accounts, more.getChildAt(0).id)
    }

    @Test
    fun bankAccountsButtonHasTheWidthOfOneShortcut() {
        val more = inflate().findViewById<android.view.ViewGroup>(R.id.row_home_shortcuts_more)

        val params = (0 until more.childCount).map { more.getChildAt(it).layoutParams as android.widget.LinearLayout.LayoutParams }

        assertEquals(5, params.size)
        assertEquals(List(5) { 0 }, params.map { it.width })
        assertEquals(List(5) { 1f }, params.map { it.weight })
    }

    @Test
    fun earnButtonInvokesCallback() {
        var clicks = 0
        val view = inflate(onEarn = { clicks++ })

        view.findViewById<android.view.View>(R.id.button_earn).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun otherButtonsDoNotTriggerEarn() {
        var clicks = 0
        val view = inflate(onEarn = { clicks++ })

        view.findViewById<android.view.View>(R.id.button_cex_prices).performClick()
        view.findViewById<android.view.View>(R.id.button_monthly_expenses).performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun earnButtonIsLabelled() {
        val view = inflate()
        val label = view.findViewById<android.view.View>(R.id.button_earn)
            .findViewById<TextView>(R.id.text_shortcut_earn)

        assertEquals("Earn", label.text.toString())
    }

    @Test
    fun monitorsButtonInvokesOnlyItsCallback() {
        var monitors = 0
        var alerts = 0
        val view = inflate(onAlerts = { alerts++ }, onMonitors = { monitors++ })

        view.findViewById<android.view.View>(R.id.button_monitors).performClick()

        assertEquals(1, monitors)
        assertEquals(0, alerts)
    }

    @Test
    fun monitorsButtonIsLabelled() {
        val view = inflate()
        val label = view.findViewById<android.view.View>(R.id.button_monitors)
            .findViewById<TextView>(R.id.text_shortcut_monitors)

        assertEquals("Monitors", label.text.toString())
    }

    @Test
    fun alertsButtonComesAfterEarn() {
        val row = mainRow()

        val ids = (0 until row.childCount).map { row.getChildAt(it).id }

        assertEquals(
            listOf(
                R.id.button_cex_prices, R.id.button_monthly_expenses, R.id.button_earn, R.id.button_alerts,
                R.id.button_monitors
            ),
            ids
        )
    }

    @Test
    fun buttonsShareTheRowWidthEqually() {
        val row = mainRow()

        val params = (0 until row.childCount).map { row.getChildAt(it).layoutParams as android.widget.LinearLayout.LayoutParams }

        assertEquals(5, params.size)
        assertEquals(List(5) { 0 }, params.map { it.width })
        assertEquals(List(5) { 1f }, params.map { it.weight })
        assertEquals(List(5) { 0 }, params.map { it.marginStart })
    }

    @Test
    fun labelsAreCenteredUnderTheirIcons() {
        val row = mainRow()

        val labels = (0 until row.childCount).map { (row.getChildAt(it) as android.view.ViewGroup).getChildAt(1) as TextView }

        assertEquals(5, labels.size)
        labels.forEach {
            assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, it.layoutParams.width)
            assertEquals(android.view.Gravity.CENTER_HORIZONTAL, it.gravity and android.view.Gravity.HORIZONTAL_GRAVITY_MASK)
        }
    }

    @Test
    fun alertsButtonInvokesOnlyItsCallback() {
        var alerts = 0
        var earn = 0
        val view = inflate(onEarn = { earn++ }, onAlerts = { alerts++ })

        view.findViewById<android.view.View>(R.id.button_alerts).performClick()

        assertEquals(1, alerts)
        assertEquals(0, earn)
    }

    @Test
    fun alertsButtonIsLabelled() {
        val view = inflate()
        val label = view.findViewById<android.view.View>(R.id.button_alerts)
            .findViewById<TextView>(R.id.text_shortcut_alerts)

        assertEquals("Alerts", label.text.toString())
    }

    @Test
    fun monthlyExpensesButtonInvokesCallback() {
        var clicks = 0
        val view = inflate(onExpenses = { clicks++ })

        view.findViewById<android.view.View>(R.id.button_monthly_expenses).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun cexButtonDoesNotTriggerMonthlyExpenses() {
        var clicks = 0
        val view = inflate(onExpenses = { clicks++ })

        view.findViewById<android.view.View>(R.id.button_cex_prices).performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun monthlyExpensesButtonIsLabelled() {
        val view = inflate()
        val label = view.findViewById<android.view.View>(R.id.button_monthly_expenses)
            .findViewById<TextView>(R.id.text_shortcut_monthly_expenses)

        assertEquals("Monthly Spend", label.text.toString())
    }

    @Test
    fun emergencyFundNoLongerShowsMonthlyExpensesLine() {
        val holder = EmergencyFundAdapter()
            .onCreateViewHolder(FrameLayout(context), 0)

        val id = context.resources.getIdentifier(
            "text_monthly_expenses", "id", context.packageName
        )
        assertEquals(0, id)
        assertNull(holder.itemView.findViewById<android.view.View>(id))
    }

    @Test
    fun emergencyFundNoLongerHasEditButton() {
        val holder = EmergencyFundAdapter()
            .onCreateViewHolder(FrameLayout(context), 0)

        val id = context.resources.getIdentifier(
            "button_edit_monthly_expenses", "id", context.packageName
        )
        assertEquals(0, id)
        assertNull(holder.itemView.findViewById<android.view.View>(id))
    }
}
