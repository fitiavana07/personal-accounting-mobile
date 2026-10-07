package dev.fitiavana.accounting.features.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AutoBackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeStore : BackupPrefsStore {
        @JvmField var enabled: Boolean = true
        @JvmField var lastBackupAt: Long = 0L
        override fun isEnabled() = enabled
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun lastBackupAt() = lastBackupAt
        override fun setLastBackupAt(millis: Long) { lastBackupAt = millis }
    }

    private class FakeScheduler : AutoBackupScheduler {
        var scheduledAt: Long? = null
        var cancelled = false
        override fun scheduleAt(triggerAtMillis: Long) { scheduledAt = triggerAtMillis; cancelled = false }
        override fun cancel() { scheduledAt = null; cancelled = true }
    }

    private val day = AutoBackupManager.INTERVAL_MILLIS
    private lateinit var dir: File
    private lateinit var store: FakeStore
    private lateinit var scheduler: FakeScheduler
    private var now = 1_700_000_000_000L
    private var exportJson = "{\"schemaVersion\":1}"
    private var exportFails = false
    private var dirAvailable = true

    @Before
    fun setUp() {
        dir = tempFolder.newFolder("backups")
        store = FakeStore()
        scheduler = FakeScheduler()
    }

    private fun manager() = AutoBackupManager(
        export = { if (exportFails) throw IllegalStateException("boom") else exportJson },
        backupDir = { if (dirAvailable) dir else null },
        store = store,
        scheduler = scheduler,
        clock = { now }
    )

    private fun backupFiles() = dir.listFiles().orEmpty().sortedBy { it.name }

    // --- isDue ---

    @Test
    fun `is due when no backup was ever made`() {
        assertTrue(manager().isDue())
    }

    @Test
    fun `is not due within 24 hours of the last backup`() {
        store.lastBackupAt = now - day + 1

        assertFalse(manager().isDue())
    }

    @Test
    fun `is due once 24 hours have passed`() {
        store.lastBackupAt = now - day

        assertTrue(manager().isDue())
    }

    @Test
    fun `is never due when auto-backup is disabled`() {
        store.enabled = false

        assertFalse(manager().isDue())
    }

    // --- runBackup ---

    @Test
    fun `runBackup writes the exported json to a timestamped file`() {
        val file = manager().runBackup()

        assertNotNull(file)
        assertEquals(exportJson, file!!.readText())
        assertTrue(file.name.startsWith(AutoBackupManager.FILE_PREFIX))
        assertTrue(file.name.endsWith(".json"))
        assertEquals(listOf(file.name), backupFiles().map { it.name })
    }

    @Test
    fun `runBackup records the time and schedules the next run 24 hours later`() {
        manager().runBackup()

        assertEquals(now, store.lastBackupAt)
        assertEquals(now + day, scheduler.scheduledAt)
    }

    @Test
    fun `runBackup keeps only the newest seven backups`() {
        val manager = manager()
        repeat(9) {
            manager.runBackup()
            now += day
        }

        val files = backupFiles()
        assertEquals(AutoBackupManager.MAX_BACKUPS, files.size)
        assertEquals(manager.latestBackup()!!.name, files.last().name)
    }

    @Test
    fun `pruning leaves files that are not auto backups alone`() {
        val manual = File(dir, "accounting_backup_2025-01-01_000000.json").apply { writeText("x") }
        val manager = manager()
        repeat(AutoBackupManager.MAX_BACKUPS + 2) {
            manager.runBackup()
            now += day
        }

        assertTrue(manual.exists())
    }

    @Test
    fun `a failing export writes nothing, keeps the last time and retries sooner`() {
        store.lastBackupAt = 123L
        exportFails = true

        val file = manager().runBackup()

        assertNull(file)
        assertEquals(0, backupFiles().size)
        assertEquals(123L, store.lastBackupAt)
        assertEquals(now + AutoBackupManager.RETRY_MILLIS, scheduler.scheduledAt)
    }

    @Test
    fun `an unavailable backup directory counts as a failure`() {
        dirAvailable = false

        assertNull(manager().runBackup())

        assertEquals(0L, store.lastBackupAt)
        assertEquals(now + AutoBackupManager.RETRY_MILLIS, scheduler.scheduledAt)
    }

    // --- runIfDue (app start catch-up) ---

    @Test
    fun `runIfDue backs up when due`() {
        assertNotNull(manager().runIfDue())

        assertEquals(1, backupFiles().size)
    }

    @Test
    fun `runIfDue does nothing but keep the alarm armed when not due`() {
        store.lastBackupAt = now - 1_000L

        assertNull(manager().runIfDue())

        assertEquals(0, backupFiles().size)
        assertEquals(store.lastBackupAt + day, scheduler.scheduledAt)
    }

    @Test
    fun `runIfDue does nothing when disabled`() {
        store.enabled = false

        assertNull(manager().runIfDue())

        assertEquals(0, backupFiles().size)
        assertNull(scheduler.scheduledAt)
    }

    // --- onAlarm ---

    @Test
    fun `onAlarm backs up even when the last backup is recent`() {
        store.lastBackupAt = now - 1_000L

        assertNotNull(manager().onAlarm())

        assertEquals(1, backupFiles().size)
    }

    @Test
    fun `onAlarm does nothing when disabled`() {
        store.enabled = false

        assertNull(manager().onAlarm())

        assertEquals(0, backupFiles().size)
        assertNull(scheduler.scheduledAt)
    }

    // --- ensureScheduled / setEnabled ---

    @Test
    fun `ensureScheduled arms the alarm 24 hours after the last backup`() {
        store.lastBackupAt = now - 1_000L

        manager().ensureScheduled()

        assertEquals(store.lastBackupAt + day, scheduler.scheduledAt)
    }

    @Test
    fun `ensureScheduled fires soon when the backup is overdue`() {
        store.lastBackupAt = now - 3 * day

        manager().ensureScheduled()

        assertEquals(now + AutoBackupManager.OVERDUE_DELAY_MILLIS, scheduler.scheduledAt)
    }

    @Test
    fun `ensureScheduled does nothing when disabled`() {
        store.enabled = false

        manager().ensureScheduled()

        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `disabling cancels the alarm and remembers the choice`() {
        val manager = manager()
        manager.ensureScheduled()

        manager.setEnabled(false)

        assertFalse(store.enabled)
        assertTrue(scheduler.cancelled)
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `enabling arms the alarm and remembers the choice`() {
        store.enabled = false

        manager().setEnabled(true)

        assertTrue(store.enabled)
        assertNotNull(scheduler.scheduledAt)
    }

    // --- latestBackup / folder ---

    @Test
    fun `latestBackup is null when there are none`() {
        assertNull(manager().latestBackup())
    }

    @Test
    fun `backupFolder is the configured directory`() {
        assertEquals(dir, manager().backupFolder())
    }
}
