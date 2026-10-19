package dev.fitiavana.accounting.ui.cloudbackup

import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupError
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class CloudBackupActivityTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val container get() = AppContainer.getInstance(context)
    private val store get() = container.cloudBackupStore

    @Before
    fun setUp() {
        // The container is shared by every test in the run.
        container.cloudBackupManager.setEnabled(false)
        store.clearFolder()
        store.passphrase = null
        store.lastSuccessAt = 0
        store.lastError = null
    }

    private fun launch(): CloudBackupActivity =
        Robolectric.buildActivity(CloudBackupActivity::class.java, CloudBackupActivity.intent(context)).setup().get().also {
            shadowOf(Looper.getMainLooper()).idle()
        }

    private fun CloudBackupActivity.view(id: Int) = findViewById<View>(id)

    private fun CloudBackupActivity.text(id: Int) = findViewById<TextView>(id).text.toString()

    private fun CloudBackupActivity.click(id: Int) {
        view(id).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun CloudBackupActivity.savePassphrase(passphrase: String) {
        findViewById<EditText>(R.id.edit_cloud_passphrase).setText(passphrase)
        click(R.id.button_cloud_save_passphrase)
    }

    private fun connectAndSetPassphrase() {
        store.setFolder("content://tree/1", "Accounting-dev")
        store.passphrase = "correct horse"
    }

    @Test
    fun `the title is Cloud Backup`() {
        assertEquals(context.getString(R.string.action_cloud_backup), launch().title.toString())
    }

    @Test
    fun `without a folder the user is invited to choose one`() {
        val activity = launch()

        assertEquals(View.GONE, activity.view(R.id.text_cloud_unsupported).visibility)
        assertEquals(View.VISIBLE, activity.view(R.id.button_cloud_choose_folder).visibility)
        assertTrue(activity.view(R.id.button_cloud_choose_folder).isEnabled)
        assertEquals(View.GONE, activity.view(R.id.group_cloud_folder).visibility)
        assertFalse(activity.view(R.id.button_cloud_restore).isEnabled)
    }

    @Test
    fun `a chosen folder is shown with a way to change or disconnect it`() {
        store.setFolder("content://tree/1", "Accounting-dev")

        val activity = launch()

        assertEquals(View.GONE, activity.view(R.id.button_cloud_choose_folder).visibility)
        assertEquals(View.VISIBLE, activity.view(R.id.group_cloud_folder).visibility)
        assertEquals("Accounting-dev", activity.text(R.id.text_cloud_folder_name))
        assertTrue(activity.view(R.id.button_cloud_restore).isEnabled)
    }

    @Test
    fun `disconnecting returns to the invitation`() {
        store.setFolder("content://tree/1", "Accounting-dev")
        val activity = launch()

        activity.click(R.id.button_cloud_disconnect)

        assertEquals(View.VISIBLE, activity.view(R.id.button_cloud_choose_folder).visibility)
        assertEquals(View.GONE, activity.view(R.id.group_cloud_folder).visibility)
        assertNull(store.folderUri)
    }

    @Test
    fun `tells how many backups are kept`() {
        assertTrue(launch().text(R.id.text_cloud_retention).contains("7"))
    }

    @Test
    fun `the passphrase is hidden until the eye is pressed`() {
        val activity = launch()
        val input = activity.findViewById<EditText>(R.id.edit_cloud_passphrase)

        assertTrue(input.transformationMethod is android.text.method.PasswordTransformationMethod)
    }

    @Test
    fun `without a passphrase the form is shown`() {
        val activity = launch()

        assertEquals(View.VISIBLE, activity.view(R.id.group_cloud_passphrase_form).visibility)
        assertEquals(View.GONE, activity.view(R.id.group_cloud_passphrase_set).visibility)
    }

    @Test
    fun `a too short passphrase shows a message and is not saved`() {
        val activity = launch()

        activity.savePassphrase("short")

        assertEquals(View.VISIBLE, activity.view(R.id.text_cloud_passphrase_error).visibility)
        assertNull(store.passphrase)
    }

    @Test
    fun `a good passphrase is saved and the form gives way to a change button`() {
        val activity = launch()

        activity.savePassphrase("correct horse")

        assertEquals("correct horse", store.passphrase)
        assertEquals(View.GONE, activity.view(R.id.group_cloud_passphrase_form).visibility)
        assertEquals(View.VISIBLE, activity.view(R.id.group_cloud_passphrase_set).visibility)
        assertEquals("", activity.findViewById<EditText>(R.id.edit_cloud_passphrase).text.toString())
    }

    @Test
    fun `the passphrase can be changed`() {
        store.passphrase = "correct horse"
        val activity = launch()

        activity.click(R.id.button_cloud_change_passphrase)
        assertEquals(View.VISIBLE, activity.view(R.id.group_cloud_passphrase_form).visibility)

        activity.savePassphrase("another passphrase")

        assertEquals("another passphrase", store.passphrase)
    }

    @Test
    fun `the daily backup switch waits for a folder and a passphrase`() {
        assertFalse(launch().view(R.id.switch_cloud_enabled).isEnabled)

        store.setFolder("content://tree/1", "Accounting-dev")
        assertFalse(launch().view(R.id.switch_cloud_enabled).isEnabled)

        store.passphrase = "correct horse"
        assertTrue(launch().view(R.id.switch_cloud_enabled).isEnabled)
    }

    @Test
    fun `switching the daily backup on is remembered`() {
        connectAndSetPassphrase()
        val activity = launch()

        activity.findViewById<SwitchCompat>(R.id.switch_cloud_enabled).isChecked = true
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(store.enabled)
    }

    @Test
    fun `back up now waits for a folder and a passphrase`() {
        assertFalse(launch().view(R.id.button_cloud_backup_now).isEnabled)

        connectAndSetPassphrase()
        assertTrue(launch().view(R.id.button_cloud_backup_now).isEnabled)
    }

    @Test
    fun `before any backup the status says so`() {
        assertEquals(context.getString(R.string.cloud_backup_status_never), launch().text(R.id.text_cloud_status))
    }

    @Test
    fun `the status shows when the last backup succeeded`() {
        store.lastSuccessAt = 1_700_000_000_000L

        val text = launch().text(R.id.text_cloud_status)

        assertTrue(text, text.startsWith(context.getString(R.string.cloud_backup_last_success, "").trim()))
    }

    @Test
    fun `the status explains the last error`() {
        store.lastError = CloudBackupError.OFFLINE

        assertEquals(context.getString(R.string.cloud_backup_error_offline), launch().text(R.id.text_cloud_error))

        store.lastError = CloudBackupError.FOLDER
        assertEquals(context.getString(R.string.cloud_backup_error_folder), launch().text(R.id.text_cloud_error))

        store.lastError = null
        assertEquals(View.GONE, launch().view(R.id.text_cloud_error).visibility)
    }

    @Test
    @Config(sdk = [21])
    fun `on Android older than 6 the feature is explained as unavailable and disabled`() {
        val activity = launch()

        assertEquals(View.VISIBLE, activity.view(R.id.text_cloud_unsupported).visibility)
        assertFalse(activity.view(R.id.button_cloud_choose_folder).isEnabled)
        assertFalse(activity.view(R.id.button_cloud_restore).isEnabled)
        assertFalse(activity.view(R.id.switch_cloud_enabled).isEnabled)
        assertFalse(activity.view(R.id.button_cloud_backup_now).isEnabled)
        assertFalse(activity.view(R.id.button_cloud_save_passphrase).isEnabled)
    }
}
