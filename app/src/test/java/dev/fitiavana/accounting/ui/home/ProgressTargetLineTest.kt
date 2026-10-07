package dev.fitiavana.accounting.ui.home

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The target sits on the same line as "reached · %", right-aligned, with the same text style. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ProgressTargetLineTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(),
        R.style.Theme_Accounting
    )

    private fun assertTargetOnPercentLine(
        root: View,
        percentId: Int,
        targetId: Int,
        expectedText: String
    ) {
        val percent = root.findViewById<TextView>(percentId)
        val target = root.findViewById<TextView>(targetId)
        assertEquals(expectedText, target.text.toString())
        assertSame(percent.parent, target.parent)
        assertEquals(percent.textSize, target.textSize, 0f)
        assertRightAligned(target)
    }

    /** The caption spans the width and is centered. */
    private fun assertCaptionCentered(root: View, captionId: Int) {
        val caption = root.findViewById<TextView>(captionId)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, caption.layoutParams.width)
        assertEquals(
            Gravity.CENTER_HORIZONTAL,
            caption.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        )
    }

    // END resolves to RIGHT in an LTR layout
    private fun assertRightAligned(view: TextView) {
        val horizontal = view.gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        assertTrue(horizontal == Gravity.END || horizontal == Gravity.RIGHT)
    }

    @Test
    fun emergencyFundTargetIsOnPercentLine() {
        val adapter = EmergencyFundAdapter()
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.submit(EmergencyFundInfo(100_000, 600_000, 300_000, 50, 300_000))
        adapter.onBindViewHolder(holder, 0)

        assertTargetOnPercentLine(
            holder.itemView,
            R.id.text_emergency_fund_6month_percent,
            R.id.text_emergency_fund_6month_target,
            UiUtils.formatAmountAr(context, 600_000)
        )
        assertCaptionCentered(
            holder.itemView,
            R.id.text_emergency_fund_6month_caption
        )
    }

    @Test
    fun incomeToExpensesTargetIsOnPercentLine() {
        val adapter = IncomeToExpensesAdapter()
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.submit(IncomeToExpensesInfo(100_000, 150_000, 150, 0))
        adapter.onBindViewHolder(holder, 0)

        assertTargetOnPercentLine(
            holder.itemView,
            R.id.text_income_to_expenses_percent,
            R.id.text_income_to_expenses_target,
            UiUtils.formatAmountAr(context, 100_000)
        )
        assertCaptionCentered(
            holder.itemView,
            R.id.text_income_to_expenses_caption
        )
    }
}
