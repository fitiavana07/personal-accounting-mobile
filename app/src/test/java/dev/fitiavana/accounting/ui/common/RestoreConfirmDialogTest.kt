package dev.fitiavana.accounting.ui.common

import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
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
class RestoreConfirmDialogTest {
    private val activity: AppCompatActivity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get().also {
        it.setTheme(R.style.Theme_Accounting)
    }

    private fun showDialog(onConfirm: () -> Unit = {}): AlertDialog {
        RestoreConfirmDialog.show(activity, "backup.json", onConfirm)
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    @Test
    fun `message names the file to restore from`() {
        val dialog = showDialog()

        val message = dialog.findViewById<android.widget.TextView>(android.R.id.message)!!.text.toString()
        assertEquals(activity.getString(R.string.restore_confirm_message, "backup.json"), message)
        assertTrue(message.contains("backup.json"))
    }

    @Test
    fun `restore button is red and confirms`() {
        var confirmed = false
        val dialog = showDialog { confirmed = true }

        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertEquals(activity.getString(R.string.action_restore), button.text.toString())
        assertEquals(ContextCompat.getColor(activity, R.color.loss), button.currentTextColor)
        button.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(confirmed)
    }
}
