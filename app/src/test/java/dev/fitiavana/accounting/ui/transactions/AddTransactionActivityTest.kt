package dev.fitiavana.accounting.ui.transactions

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import androidx.appcompat.app.AlertDialog
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AddTransactionActivityTest {

    private fun launch(): ActivityController<AddTransactionActivity> =
        Robolectric.buildActivity(AddTransactionActivity::class.java).setup()

    private fun AddTransactionActivity.step1() = findViewById<View>(R.id.step_mode_selection)
    private fun AddTransactionActivity.step2() = findViewById<View>(R.id.step_transaction_form)

    @Test
    fun `launches showing step 1 with step 2 hidden`() {
        val activity = launch().get()

        assertEquals(View.VISIBLE, activity.step1().visibility)
        assertEquals(View.GONE, activity.step2().visibility)
    }

    @Test
    fun `tapping the classic mode card shows step 2 with only the classic section visible`() {
        val activity = launch().get()

        activity.findViewById<View>(R.id.mode_option_classic).performClick()

        assertEquals(View.GONE, activity.step1().visibility)
        assertEquals(View.VISIBLE, activity.step2().visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mode_classic).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.mode_simple_transfer).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.mode_instrument_transfer).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.mode_instrument_income).visibility)
    }

    @Test
    fun `tapping the simple transfer mode card shows only the simple transfer section`() {
        val activity = launch().get()

        activity.findViewById<View>(R.id.mode_option_simple_transfer).performClick()

        assertEquals(View.GONE, activity.step1().visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mode_simple_transfer).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.mode_classic).visibility)
    }

    @Test
    fun `tapping the instrument transfer mode card shows only the instrument transfer section`() {
        val activity = launch().get()

        activity.findViewById<View>(R.id.mode_option_instrument_transfer).performClick()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mode_instrument_transfer).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.mode_classic).visibility)
    }

    @Test
    fun `tapping the instrument income mode card shows only the instrument income section`() {
        val activity = launch().get()

        activity.findViewById<View>(R.id.mode_option_instrument_income).performClick()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.mode_instrument_income).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.mode_classic).visibility)
    }

    @Test
    fun `back from step 2 with no data entered returns to step 1 without a dialog`() {
        val activity = launch().get()
        activity.findViewById<View>(R.id.mode_option_classic).performClick()

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(View.VISIBLE, activity.step1().visibility)
        assertEquals(View.GONE, activity.step2().visibility)
        assertEquals(null, ShadowDialog.getLatestDialog())
    }

    @Test
    fun `back from step 2 with unsaved data shows a discard dialog, and discarding returns to step 1`() {
        val activity = launch().get()
        activity.findViewById<View>(R.id.mode_option_classic).performClick()
        activity.findViewById<EditText>(R.id.edit_note).setText("some note")

        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog?
        assertEquals(View.VISIBLE, activity.step2().visibility)
        org.junit.Assert.assertNotNull(dialog)

        dialog!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, activity.step1().visibility)
        assertEquals(View.GONE, activity.step2().visibility)
        assertEquals("", activity.findViewById<EditText>(R.id.edit_note).text.toString())
    }

    @Test
    fun `keep editing on the discard dialog stays on step 2`() {
        val activity = launch().get()
        activity.findViewById<View>(R.id.mode_option_classic).performClick()
        activity.findViewById<EditText>(R.id.edit_note).setText("some note")

        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()

        assertEquals(View.GONE, activity.step1().visibility)
        assertEquals(View.VISIBLE, activity.step2().visibility)
    }

    @Test
    fun `back from step 1 finishes the activity without a dialog`() {
        val activity = launch().get()

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(true, activity.isFinishing)
        assertEquals(null, ShadowDialog.getLatestDialog())
    }

    @Test
    fun `pressing down on a mode card scales it down, releasing restores it and still selects the mode`() {
        val activity = launch().get()
        val card = activity.findViewById<View>(R.id.mode_option_classic)

        card.dispatchTouchEvent(
            MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        )
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(150))
        assertEquals(0.96f, card.scaleX)

        card.dispatchTouchEvent(
            MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
        )
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(150))
        assertEquals(1f, card.scaleX)

        card.performClick()
        assertEquals(View.VISIBLE, activity.step2().visibility)
    }

    @Test
    fun `up navigation from step 2 with no data returns to step 1 instead of finishing`() {
        val activity = launch().get()
        activity.findViewById<View>(R.id.mode_option_classic).performClick()

        activity.onSupportNavigateUp()

        assertEquals(View.VISIBLE, activity.step1().visibility)
        assertEquals(false, activity.isFinishing)
    }
}
