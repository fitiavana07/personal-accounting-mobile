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
        onExpenses: () -> Unit = {}
    ) = HomeShortcutsAdapter(onCex, onExpenses)
        .let { it.onCreateViewHolder(FrameLayout(context), 0).itemView }

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

        assertEquals("Mo. Expenses", label.text.toString())
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
