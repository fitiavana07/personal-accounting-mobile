package dev.fitiavana.accounting.features.cloudbackup

import dev.fitiavana.accounting.features.backup.RestoreResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class CloudBackupManagerTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    // 2026-03-04 05:06:07 UTC
    private var now = 1_772_600_767_000L
    private var online = true
    private var exportFails = false
    private var exportCalls = 0
    private var restoreResult: RestoreResult = RestoreResult.Success
    private val restored = mutableListOf<String>()
    private val json = """{"schemaVersion":22}"""

    private val store = FakeCloudBackupStore().apply {
        setFolder("content://tree/1", "Accounting-dev")
        enabled = true
        enabledAt = now
        passphrase = "correct horse"
    }
    private val folder = FakeCloudFolder()
    private val scheduler = FakeCloudBackupScheduler()
    private val notifier = FakeCloudBackupNotifier()
    private val repository = CloudBackupRepository(store) { folder }
    private lateinit var defaultZone: TimeZone

    private val manager
        get() = CloudBackupManager(
            export = { exportCalls++; if (exportFails) throw IllegalStateException("boom") else json },
            repository = repository,
            store = store,
            scheduler = scheduler,
            network = { online },
            notifier = notifier,
            restore = { restored += it; restoreResult },
            filePrefix = "acc_",
            iterations = 10,
            clock = { now }
        )

    @Before
    fun setUp() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(defaultZone)

    // --- a run -----------------------------------------------------------------------------

    @Test
    fun `a run writes one encrypted file named after the time`() {
        val outcome = manager.runBackup()

        assertTrue(outcome is CloudBackupOutcome.Success)
        assertEquals(listOf("acc_2026-03-04_050607.accbak"), folder.names())
        val bytes = folder.contents.values.single()
        assertTrue(BackupEnvelope.isEnvelope(bytes))
        assertEquals(json, BackupEnvelope.decode(bytes, "correct horse"))
    }

    @Test
    fun `a success is recorded and the next run armed for tomorrow`() {
        manager.runBackup()

        assertEquals(now, store.lastSuccessAt)
        assertNull(store.lastError)
        assertEquals(0, store.consecutiveFailures)
        assertEquals(listOf(now + day), scheduler.scheduled)
    }

    @Test
    fun `a success keeps only the newest 7 of this build's backups`() {
        (1..9).forEach { folder.add("acc_2026-01-0$it.accbak") }
        folder.add("release_2026-01-01.accbak")
        folder.add("notes.txt")

        manager.runBackup()

        val expected = (4..9).map { "acc_2026-01-0$it.accbak" } +
            listOf("acc_2026-03-04_050607.accbak", "notes.txt", "release_2026-01-01.accbak")
        assertEquals(expected.sorted(), folder.names())
    }

    @Test
    fun `a failure to prune does not fail the run`() {
        (1..9).forEach { folder.add("acc_2026-01-0$it.accbak") }
        folder.failDelete = true

        val outcome = manager.runBackup()

        assertTrue(outcome is CloudBackupOutcome.Success)
        assertEquals(now, store.lastSuccessAt)
    }

    @Test
    fun `offline the run is skipped, not counted as a failure, and retried in an hour`() {
        online = false

        val outcome = manager.runBackup()

        assertEquals(CloudBackupOutcome.Offline, outcome)
        assertEquals(0, exportCalls)
        assertTrue(folder.files.isEmpty())
        assertEquals(CloudBackupError.OFFLINE, store.lastError)
        assertEquals(0, store.consecutiveFailures)
        assertEquals(listOf(now + hour), scheduler.scheduled)
    }

    @Test
    fun `an unreachable folder fails the run, is counted and retried in an hour`() {
        folder.failCreate = true

        val outcome = manager.runBackup()

        assertEquals(CloudBackupOutcome.Failed, outcome)
        assertEquals(CloudBackupError.FOLDER, store.lastError)
        assertEquals(1, store.consecutiveFailures)
        assertEquals(0L, store.lastSuccessAt)
        assertEquals(listOf(now + hour), scheduler.scheduled)
    }

    @Test
    fun `a failing export is an unexpected failure`() {
        exportFails = true

        assertEquals(CloudBackupOutcome.Failed, manager.runBackup())
        assertEquals(CloudBackupError.UNEXPECTED, store.lastError)
        assertEquals(1, store.consecutiveFailures)
    }

    @Test
    fun `no folder or no passphrase is a configuration error that does not retry`() {
        store.passphrase = null

        assertEquals(CloudBackupOutcome.NotConfigured, manager.runBackup())
        assertEquals(CloudBackupError.NOT_CONFIGURED, store.lastError)
        assertEquals(0, store.consecutiveFailures)
        assertTrue(scheduler.scheduled.isEmpty())

        store.passphrase = "correct horse"
        store.clearFolder()

        assertEquals(CloudBackupOutcome.NotConfigured, manager.runBackup())
        assertEquals(0, exportCalls)
    }

    // --- notifications -----------------------------------------------------------------------

    @Test
    fun `the third failure in a row notifies once`() {
        folder.failCreate = true

        manager.runBackup()
        manager.runBackup()
        assertEquals(0, notifier.failing)

        manager.runBackup()
        manager.runBackup()
        assertEquals(1, notifier.failing)
        assertTrue(store.failureNotified)
    }

    @Test
    fun `being offline for more than three days notifies once`() {
        online = false

        now += 3 * day
        manager.runBackup()
        assertEquals(0, notifier.failing)

        now += hour
        manager.runBackup()
        manager.runBackup()
        assertEquals(1, notifier.failing)
    }

    @Test
    fun `three days are counted from the last success`() {
        store.lastSuccessAt = now
        online = false

        now += 2 * day
        manager.runBackup()

        assertEquals(0, notifier.failing)
    }

    @Test
    fun `a success after failures resets them and clears the notification`() {
        folder.failCreate = true
        repeat(3) { manager.runBackup() }
        folder.failCreate = false

        manager.runBackup()

        assertEquals(0, store.consecutiveFailures)
        assertFalse(store.failureNotified)
        assertNull(store.lastError)
        assertEquals(1, notifier.cleared)
    }

    @Test
    fun `a success with nothing to clear does not touch the notification`() {
        manager.runBackup()

        assertEquals(0, notifier.cleared)
    }

    // --- schedule ----------------------------------------------------------------------------

    @Test
    fun `due when enabled and never run or run a day ago`() {
        assertTrue(manager.isDue())

        store.lastSuccessAt = now - day + 1
        assertFalse(manager.isDue())

        store.lastSuccessAt = now - day
        assertTrue(manager.isDue())

        store.enabled = false
        assertFalse(manager.isDue())
    }

    @Test
    fun `app start runs a due backup`() {
        manager.runIfDue()

        assertEquals(1, folder.files.size)
    }

    @Test
    fun `app start with nothing due only makes sure the alarm is armed`() {
        store.lastSuccessAt = now - hour

        manager.runIfDue()

        assertTrue(folder.files.isEmpty())
        assertEquals(listOf(now - hour + day), scheduler.scheduled)
    }

    @Test
    fun `app start does nothing when disabled`() {
        store.enabled = false

        manager.runIfDue()

        assertTrue(folder.files.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `an overdue schedule is armed for one minute from now`() {
        store.lastSuccessAt = now - 5 * day

        manager.ensureScheduled()

        assertEquals(listOf(now + 60_000), scheduler.scheduled)
    }

    @Test
    fun `the alarm runs a backup only when enabled`() {
        store.enabled = false
        manager.onAlarm()
        assertTrue(folder.files.isEmpty())

        store.enabled = true
        manager.onAlarm()
        assertEquals(1, folder.files.size)
    }

    @Test
    fun `enabling remembers when and arms the alarm, disabling cancels it`() {
        store.enabled = false

        manager.setEnabled(true)

        assertTrue(store.enabled)
        assertEquals(now, store.enabledAt)
        assertEquals(1, scheduler.scheduled.size)

        manager.setEnabled(false)

        assertFalse(store.enabled)
        assertEquals(1, scheduler.cancelled)
    }

    // --- restore -----------------------------------------------------------------------------

    private fun cloudFile(content: ByteArray) = folder.add("acc_x.accbak", content)

    private fun backupBytes(passphrase: String = "correct horse") = BackupEnvelope.encode(json, passphrase, iterations = 10)

    @Test
    fun `restores with the stored passphrase without asking`() {
        restoreResult = RestoreResult.SuccessFromOlderVersion(20, 22)

        val result = manager.restore(cloudFile(backupBytes()), passphrase = null)

        assertEquals(CloudRestoreResult.Restored(RestoreResult.SuccessFromOlderVersion(20, 22)), result)
        assertEquals(listOf(json), restored)
    }

    @Test
    fun `a typed passphrase wins over the stored one`() {
        store.passphrase = "something else"

        val result = manager.restore(cloudFile(backupBytes("typed one")), passphrase = "typed one")

        assertEquals(CloudRestoreResult.Restored(RestoreResult.Success), result)
    }

    @Test
    fun `without any passphrase it asks for one`() {
        store.passphrase = null

        assertEquals(CloudRestoreResult.NeedsPassphrase, manager.restore(cloudFile(backupBytes()), passphrase = null))
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `a wrong passphrase touches nothing`() {
        assertEquals(
            CloudRestoreResult.WrongPassphrase,
            manager.restore(cloudFile(backupBytes("another")), passphrase = null)
        )
        assertEquals(
            CloudRestoreResult.WrongPassphrase,
            manager.restore(cloudFile(backupBytes("another")), passphrase = "still wrong")
        )
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `a file that is not a cloud backup is reported`() {
        assertEquals(CloudRestoreResult.NotABackup, manager.restore(cloudFile("{}".toByteArray()), passphrase = null))
    }

    @Test
    fun `a file from a newer app is reported`() {
        val bytes = backupBytes().also { it[5] = 2 }

        assertEquals(CloudRestoreResult.UnsupportedFormat, manager.restore(cloudFile(bytes), passphrase = null))
    }

    @Test
    fun `an unreachable folder is reported`() {
        val file = cloudFile(backupBytes())
        folder.failing = true

        assertEquals(CloudRestoreResult.Unreachable, manager.restore(file, passphrase = null))
    }

    @Test
    fun `the restore result of the database is passed on`() {
        restoreResult = RestoreResult.SchemaMismatch(30, 22)

        assertEquals(
            CloudRestoreResult.Restored(RestoreResult.SchemaMismatch(30, 22)),
            manager.restore(cloudFile(backupBytes()), passphrase = null)
        )
    }

    @Test
    fun `lists the backups of the repository`() {
        folder.add("a.accbak")

        assertEquals(listOf("a.accbak"), manager.listBackups().map { it.name })
    }

    // --- status and passphrase -----------------------------------------------------------------

    @Test
    fun `the status tells what the screen shows`() {
        store.lastSuccessAt = 0
        store.lastError = CloudBackupError.OFFLINE

        assertEquals(CloudBackupStatus(enabled = true, hasPassphrase = true, lastSuccessAt = null, lastError = CloudBackupError.OFFLINE), manager.status())

        store.lastSuccessAt = 42
        store.passphrase = null
        store.enabled = false

        assertEquals(CloudBackupStatus(enabled = false, hasPassphrase = false, lastSuccessAt = 42, lastError = CloudBackupError.OFFLINE), manager.status())
    }

    @Test
    fun `a passphrase is stored for the backups and for restoring`() {
        store.passphrase = null

        manager.setPassphrase("another passphrase")

        assertEquals("another passphrase", store.passphrase)
        assertTrue(manager.status().hasPassphrase)
    }
}
