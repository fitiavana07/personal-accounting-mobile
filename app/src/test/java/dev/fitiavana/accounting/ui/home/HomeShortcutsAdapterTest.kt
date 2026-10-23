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
        onAlerts: () -> Unit = {}
    ) = HomeShortcutsAdapter(onCex, onExpenses, onEarn, onAlerts)
        .let { it.onCreateViewHolder(FrameLayout(context), 0).itemView }

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
    fun alertsButtonComesAfterEarn() {
        val row = inflate() as android.view.ViewGroup

        val ids = (0 until row.childCount).map { row.getChildAt(it).id }

        assertEquals(
            listOf(R.id.button_cex_prices, R.id.button_monthly_expenses, R.id.button_earn, R.id.button_alerts),
            ids
        )
    }

    @Test
    fun buttonsShareTheRowWidthEqually() {
        val row = inflate() as android.view.ViewGroup

        val params = (0 until row.childCount).map { row.getChildAt(it).layoutParams as android.widget.LinearLayout.LayoutParams }

        assertEquals(4, params.size)
        assertEquals(listOf(0, 0, 0, 0), params.map { it.width })
        assertEquals(listOf(1f, 1f, 1f, 1f), params.map { it.weight })
        assertEquals(listOf(0, 0, 0, 0), params.map { it.marginStart })
    }

    @Test
    fun labelsAreCenteredUnderTheirIcons() {
        val row = inflate() as android.view.ViewGroup

        val labels = (0 until row.childCount).map { (row.getChildAt(it) as android.view.ViewGroup).getChildAt(1) as TextView }

        assertEquals(4, labels.size)
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
