package dev.fitiavana.accounting.ui.sync

import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncActivityTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val settings get() = AppContainer.getInstance(context).syncSettingsStore

    @Before
    fun setUp() {
        // The container is shared by every test in the run.
        settings.setPassphrase(null)
        settings.setAutoSync(true)
    }

    private fun launch(): SyncActivity =
        Robolectric.buildActivity(SyncActivity::class.java, SyncActivity.intent(context)).setup().get().also {
            shadowOf(Looper.getMainLooper()).idle()
        }

    private fun SyncActivity.view(id: Int) = findViewById<View>(id)

    /** Clicks, then lets the state travel from the view model back to the screen. */
    private fun SyncActivity.click(id: Int) {
        view(id).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun SyncActivity.type(passphrase: String) {
        findViewById<EditText>(R.id.edit_sync_passphrase).setText(passphrase)
        click(R.id.button_sync_save_passphrase)
    }

    /** The confirmation dialog is an appcompat one, which Robolectric knows only as a plain dialog. */
    private fun latestDialog() = ShadowDialog.getLatestDialog() as AlertDialog

    @Test
    fun `the title is Sync`() {
        assertEquals(context.getString(R.string.title_sync), launch().title.toString())
    }

    @Test
    fun `shows this device's name`() {
        val activity = launch()

        assertTrue(activity.findViewById<TextView>(R.id.text_sync_device_name).text.isNotBlank())
    }

    @Test
    fun `without a passphrase the form is shown and the devices list invites to set one`() {
        val activity = launch()

        assertEquals(View.VISIBLE, activity.view(R.id.group_passphrase_form).visibility)
        assertEquals(View.GONE, activity.view(R.id.group_passphrase_set).visibility)
        assertEquals(context.getString(R.string.sync_empty_no_passphrase), activity.findViewById<TextView>(R.id.text_sync_empty).text.toString())
        assertFalse(activity.view(R.id.button_sync_all).isEnabled)
    }

    @Test
    fun `a too short passphrase shows a message and is not saved`() {
        val activity = launch()

        activity.type("short")

        assertEquals(View.VISIBLE, activity.view(R.id.text_sync_passphrase_error).visibility)
        assertNull(settings.passphrase.value)
    }

    @Test
    fun `a good passphrase is saved and the form gives way to the set state`() {
        val activity = launch()

        activity.type("correct horse")

        assertEquals("correct horse", settings.passphrase.value)
        assertEquals(View.GONE, activity.view(R.id.group_passphrase_form).visibility)
        assertEquals(View.VISIBLE, activity.view(R.id.group_passphrase_set).visibility)
        assertEquals("", activity.findViewById<EditText>(R.id.edit_sync_passphrase).text.toString())
    }

    @Test
    fun `with no peers around the list says so`() {
        settings.setPassphrase("correct horse")
        val activity = launch()

        assertEquals(context.getString(R.string.sync_empty), activity.findViewById<TextView>(R.id.text_sync_empty).text.toString())
        assertEquals(View.VISIBLE, activity.view(R.id.text_sync_empty).visibility)
    }

    @Test
    fun `changing the passphrase offers the form with a cancel button`() {
        settings.setPassphrase("correct horse")
        val activity = launch()

        activity.click(R.id.button_sync_change_passphrase)
        assertEquals(View.VISIBLE, activity.view(R.id.group_passphrase_form).visibility)
        assertEquals(View.VISIBLE, activity.view(R.id.button_sync_cancel_passphrase).visibility)

        activity.click(R.id.button_sync_cancel_passphrase)
        assertEquals(View.GONE, activity.view(R.id.group_passphrase_form).visibility)
    }

    @Test
    fun `removing the passphrase asks first and then forgets it`() {
        settings.setPassphrase("correct horse")
        val activity = launch()

        activity.click(R.id.button_sync_remove_passphrase)
        val dialog = latestDialog()
        assertEquals("correct horse", settings.passphrase.value)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(settings.passphrase.value)
        assertEquals(View.VISIBLE, activity.view(R.id.group_passphrase_form).visibility)
    }

    @Test
    fun `cancelling the removal keeps the passphrase`() {
        settings.setPassphrase("correct horse")
        val activity = launch()

        activity.click(R.id.button_sync_remove_passphrase)
        latestDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()

        assertEquals("correct horse", settings.passphrase.value)
    }

    @Test
    fun `the switch controls auto-sync`() {
        val activity = launch()
        val switch = activity.findViewById<SwitchCompat>(R.id.switch_sync_auto)
        assertTrue(switch.isChecked)

        switch.isChecked = false

        assertFalse(settings.autoSync.value)
    }

    @Test
    fun `the buttons for syncing and refreshing exist`() {
        val activity = launch()

        assertEquals(context.getString(R.string.sync_all), activity.findViewById<Button>(R.id.button_sync_all).text.toString())
        assertEquals(context.getString(R.string.sync_refresh), activity.findViewById<Button>(R.id.button_sync_refresh).text.toString())
    }
}
