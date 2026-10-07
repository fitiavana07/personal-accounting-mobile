package dev.fitiavana.accounting.features.backup

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class BackupReceiversTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeStore : BackupPrefsStore {
        @JvmField var enabled = true
        @JvmField var last = 0L
        override fun isEnabled() = enabled
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun lastBackupAt() = last
        override fun setLastBackupAt(millis: Long) { last = millis }
    }

    private class FakeScheduler : AutoBackupScheduler {
        var scheduledAt: Long? = null
        override fun scheduleAt(triggerAtMillis: Long) { scheduledAt = triggerAtMillis }
        override fun cancel() { scheduledAt = null }
    }

    private lateinit var context: Context
    private lateinit var dir: File
    private lateinit var store: FakeStore
    private lateinit var scheduler: FakeScheduler
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dir = tempFolder.newFolder("backups")
        store = FakeStore()
        scheduler = FakeScheduler()
    }

    private fun manager() = AutoBackupManager(
        export = { "{}" },
        backupDir = { dir },
        store = store,
        scheduler = scheduler,
        clock = { now }
    )

    private fun alarmReceiver() = AutoBackupReceiver().apply {
        managerProvider = { manager() }
        runInBackground = { it() }
    }

    private fun bootReceiver() = BootReceiver().apply { managerProvider = { manager() } }

    @Test
    fun `the alarm broadcast writes a backup and arms the next alarm`() {
        alarmReceiver().onReceive(context, Intent(AutoBackupReceiver.ACTION_AUTO_BACKUP))

        assertEquals(1, dir.listFiles().orEmpty().size)
        assertEquals(now + AutoBackupManager.INTERVAL_MILLIS, scheduler.scheduledAt)
    }

    @Test
    fun `the alarm broadcast does nothing when auto-backup is disabled`() {
        store.enabled = false

        alarmReceiver().onReceive(context, Intent(AutoBackupReceiver.ACTION_AUTO_BACKUP))

        assertEquals(0, dir.listFiles().orEmpty().size)
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `an unrelated broadcast to the alarm receiver is ignored`() {
        alarmReceiver().onReceive(context, Intent("some.other.ACTION"))

        assertEquals(0, dir.listFiles().orEmpty().size)
    }

    @Test
    fun `boot completed re-arms the alarm without backing up`() {
        store.last = now - 1_000L

        bootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertNotNull(scheduler.scheduledAt)
        assertEquals(now - 1_000L + AutoBackupManager.INTERVAL_MILLIS, scheduler.scheduledAt)
        assertEquals(0, dir.listFiles().orEmpty().size)
    }

    @Test
    fun `app update re-arms the alarm too`() {
        bootReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertNotNull(scheduler.scheduledAt)
    }

    @Test
    fun `an unrelated broadcast to the boot receiver is ignored`() {
        bootReceiver().onReceive(context, Intent("some.other.ACTION"))

        assertNull(scheduler.scheduledAt)
    }
}
