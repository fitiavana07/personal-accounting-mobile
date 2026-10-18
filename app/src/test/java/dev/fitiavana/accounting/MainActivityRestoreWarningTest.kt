package dev.fitiavana.accounting

import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
class MainActivityRestoreWarningTest {

    @Test
    fun `restoring an older backup shows the full warning in a dialog`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        activity.showRestoreOlderVersionWarning(21, 22)
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        assertEquals(
            activity.getString(R.string.restore_success_older_version, 21, 22),
            dialog.findViewById<TextView>(android.R.id.message)!!.text.toString()
        )
    }
}
