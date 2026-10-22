package dev.fitiavana.accounting.ui.common

import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class EditAprDialogTest {
    private val activity: AppCompatActivity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get().also {
        it.setTheme(R.style.Theme_Accounting)
    }

    private fun showDialog(currentApr: Double = 5.5, onSave: (Double) -> Unit = {}): AlertDialog {
        EditAprDialog.show(activity, "Earn", currentApr, onSave)
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun input(dialog: AlertDialog) = dialog.findViewById<EditText>(R.id.input_apr)!!

    private fun layout(dialog: AlertDialog) =
        dialog.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layout_apr)!!

    private fun save(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `input is prefilled with the current rate`() {
        assertEquals("5.5", input(showDialog(5.5)).text.toString())
    }

    @Test
    fun `title is the account name and the field shows a percent suffix`() {
        val dialog = showDialog()

        assertEquals("Earn", dialog.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString())
        assertEquals("%", layout(dialog).suffixText.toString())
        assertEquals(activity.getString(R.string.dialog_edit_apr_title), layout(dialog).hint.toString())
    }

    @Test
    fun `save is the accent colour and cancel is visibly secondary`() {
        val dialog = showDialog()
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE).currentTextColor
        val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE).currentTextColor

        assertEquals(androidx.core.content.ContextCompat.getColor(activity, R.color.dialog_button), save)
        assertTrue("cancel must differ from save", cancel != save)
    }

    @Test
    fun `an error is cleared once the user edits the input`() {
        val dialog = showDialog()
        input(dialog).setText("")
        save(dialog)

        input(dialog).setText("5")

        assertNull(layout(dialog).error)
    }

    @Test
    fun `saving a valid rate calls back and closes`() {
        var saved: Double? = null
        val dialog = showDialog { saved = it }
        input(dialog).setText("7.25")

        save(dialog)

        assertEquals(7.25, saved!!, 0.0)
        assertTrue(!dialog.isShowing)
    }

    @Test
    fun `a comma is accepted as the decimal separator`() {
        var saved: Double? = null
        val dialog = showDialog { saved = it }
        input(dialog).setText("3,5")

        save(dialog)

        assertEquals(3.5, saved!!, 0.0)
    }

    @Test
    fun `invalid input keeps the dialog open with an error`() {
        for (bad in listOf("", "abc", "0", "-2")) {
            var saved: Double? = null
            val dialog = showDialog { saved = it }
            input(dialog).setText(bad)

            save(dialog)

            assertNull(bad, saved)
            assertTrue(bad, dialog.isShowing)
            assertNotNull(bad, layout(dialog).error)
        }
    }

    @Test
    fun `cancel does not call back`() {
        var saved = false
        val dialog = showDialog { saved = true }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(!saved)
    }
}
