package dev.fitiavana.accounting.features.cloudbackup

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.features.backup.AutoBackupManager
import dev.fitiavana.accounting.features.backup.BootReceiver
import dev.fitiavana.accounting.features.backup.BackupPrefsStore
import dev.fitiavana.accounting.features.backup.AutoBackupScheduler
import dev.fitiavana.accounting.features.backup.RestoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class CloudBackupReceiverTest {

    private val now = 1_700_000_000_000L
    private val store = FakeCloudBackupStore().apply {
        setFolder("content://tree/1", "Accounting-dev")
        enabled = true
        passphrase = "correct horse"
    }
    private val folder = FakeCloudFolder()
    private val scheduler = FakeCloudBackupScheduler()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun manager() = CloudBackupManager(
        export = { "{}" },
        repository = CloudBackupRepository(store) { folder },
        store = store,
        scheduler = scheduler,
        network = { true },
        notifier = FakeCloudBackupNotifier(),
        restore = { RestoreResult.Success },
        filePrefix = "acc_",
        iterations = 10,
        clock = { now }
    )

    private fun receiver() = CloudBackupReceiver().apply {
        managerProvider = { manager() }
        runInBackground = { it() }
    }

    @Test
    fun `the alarm broadcast writes a backup and arms the next alarm`() {
        receiver().onReceive(context, Intent(CloudBackupReceiver.ACTION_CLOUD_BACKUP))

        assertEquals(1, folder.files.size)
        assertEquals(listOf(now + CloudBackupManager.INTERVAL_MILLIS), scheduler.scheduled)
    }

    @Test
    fun `the alarm broadcast does nothing when cloud backup is disabled`() {
        store.enabled = false

        receiver().onReceive(context, Intent(CloudBackupReceiver.ACTION_CLOUD_BACKUP))

        assertTrue(folder.files.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `an unrelated broadcast is ignored`() {
        receiver().onReceive(context, Intent("some.other.ACTION"))

        assertTrue(folder.files.isEmpty())
    }

    private class NoStore : BackupPrefsStore {
        override fun isEnabled() = false
        override fun setEnabled(enabled: Boolean) = Unit
        override fun lastBackupAt() = 0L
        override fun setLastBackupAt(millis: Long) = Unit
    }

    private class NoScheduler : AutoBackupScheduler {
        override fun scheduleAt(triggerAtMillis: Long) = Unit
        override fun cancel() = Unit
    }

    @Test
    fun `boot completed re-arms the cloud alarm too`() {
        val boot = BootReceiver().apply {
            managerProvider = { AutoBackupManager(export = { "{}" }, backupDir = { null }, store = NoStore(), scheduler = NoScheduler()) }
            cloudManagerProvider = { manager() }
        }

        boot.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(listOf(CloudBackupManager.OVERDUE_DELAY_MILLIS + now), scheduler.scheduled)
        assertTrue(folder.files.isEmpty())
    }
}
