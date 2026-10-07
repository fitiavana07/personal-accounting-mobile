package dev.fitiavana.accounting

import android.os.Looper
import android.view.Menu
import android.widget.TextView
import android.app.Dialog
import androidx.appcompat.widget.SwitchCompat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenu
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class MainActivityAutoBackupTest {

    private lateinit var activity: MainActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        // Start from a known state regardless of earlier tests sharing the AppContainer singleton.
        AppContainer.getInstance(activity).autoBackupManager.setEnabled(true)
    }

    /** The AppCompat toolbar menu isn't created by Robolectric on its own, so inflate it explicitly. */
    private fun inflateMenu(): Menu = RoboMenu(activity).also { activity.onCreateOptionsMenu(it) }

    private fun openAutoBackupDialog(): Dialog {
        val item = inflateMenu().findItem(R.id.action_auto_backup)
        activity.onOptionsItemSelected(item)
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog()
    }

    @Test
    fun `the overflow menu has an auto-backup entry`() {
        assertNotNull(inflateMenu().findItem(R.id.action_auto_backup))
    }

    @Test
    fun `selecting it shows the switch on by default`() {
        val dialog = openAutoBackupDialog()

        assertTrue(dialog.findViewById<SwitchCompat>(R.id.switch_auto_backup)!!.isChecked)
    }

    @Test
    fun `the dialog shows the folder path`() {
        val dialog = openAutoBackupDialog()

        val folder = dialog.findViewById<TextView>(R.id.text_auto_backup_folder)!!.text.toString()
        assertTrue(folder.contains("backups"))
    }

    @Test
    fun `turning the switch off disables auto-backup`() {
        val dialog = openAutoBackupDialog()

        dialog.findViewById<SwitchCompat>(R.id.switch_auto_backup)!!.isChecked = false

        assertFalse(AppContainer.getInstance(activity).autoBackupManager.isEnabled())
    }

    @Test
    fun `turning the switch back on enables auto-backup`() {
        AppContainer.getInstance(activity).autoBackupManager.setEnabled(false)
        val dialog = openAutoBackupDialog()

        dialog.findViewById<SwitchCompat>(R.id.switch_auto_backup)!!.isChecked = true

        assertTrue(AppContainer.getInstance(activity).autoBackupManager.isEnabled())
    }

    @Test
    fun `the dialog shows a last backup line`() {
        val dialog = openAutoBackupDialog()

        // "Never" or a date, depending on whether the launch catch-up has already run.
        assertTrue(dialog.findViewById<TextView>(R.id.text_auto_backup_last)!!.text.isNotEmpty())
    }
}
