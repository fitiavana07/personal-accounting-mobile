package dev.fitiavana.accounting.ui.common

import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
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

/** Text buttons of every dialog (Save, Cancel, OK...) must stay readable on the dialog surface in both themes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class DialogButtonContrastTest {

    // Approximate dark dialog surface (Material dark surface with elevation overlay).
    private val darkDialogSurface = 0xFF424242.toInt()
    private val minContrast = 4.5

    private fun showDialog(): AlertDialog {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(R.style.Theme_Accounting)
        EditAprDialog.show(activity, "Earn", 5.5) {}
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    @Test
    @Config(qualifiers = "night")
    fun `dialog buttons are readable in dark mode`() {
        val dialog = showDialog()

        for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) {
            // Secondary text colours are translucent; compare what is actually seen over the surface.
            val color = ColorUtils.compositeColors(dialog.getButton(which).currentTextColor, darkDialogSurface)
            val contrast = ColorUtils.calculateContrast(color, darkDialogSurface)
            assertTrue("button $which contrast $contrast", contrast >= minContrast)
        }
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `dialog buttons keep the primary gold in light mode`() {
        val dialog = showDialog()

        assertEquals(
            ContextCompat.getColor(dialog.context, R.color.gold_500),
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).currentTextColor
        )
    }
}
