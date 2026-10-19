package dev.fitiavana.accounting.ui.cloudbackup

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import dev.fitiavana.accounting.features.backup.RestoreResult
import dev.fitiavana.accounting.features.cloudbackup.BackupEnvelope
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupError
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupFile
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupManager
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupOutcome
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupRepository
import dev.fitiavana.accounting.features.cloudbackup.CloudRestoreResult
import dev.fitiavana.accounting.features.cloudbackup.FakeCloudBackupNotifier
import dev.fitiavana.accounting.features.cloudbackup.FakeCloudBackupScheduler
import dev.fitiavana.accounting.features.cloudbackup.FakeCloudBackupStore
import dev.fitiavana.accounting.features.cloudbackup.FakeCloudFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CloudBackupViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val now = 1_700_000_000_000L
    private var online = true
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val store = FakeCloudBackupStore()
    private val folder = FakeCloudFolder()
    private val scheduler = FakeCloudBackupScheduler()
    private val repository = CloudBackupRepository(store) { folder }
    private val manager = CloudBackupManager(
        export = { "{}" },
        repository = repository,
        store = store,
        scheduler = scheduler,
        network = { online },
        notifier = FakeCloudBackupNotifier(),
        restore = { RestoreResult.Success },
        filePrefix = "acc_",
        iterations = 10,
        clock = { now }
    )

    @After
    fun tearDown() = scope.cancel()

    private fun viewModel(supported: Boolean = true) =
        CloudBackupViewModel(repository, manager, supported, scope, Dispatchers.Unconfined)

    private fun CloudBackupViewModel.current() = state.value!!

    /** A connected folder with a passphrase: everything the daily backup needs. */
    private fun configure() {
        repository.connect("content://tree/1")
        manager.setPassphrase("correct horse")
    }

    // --- folder --------------------------------------------------------------------------------

    @Test
    fun `starts disconnected and supported`() {
        val state = viewModel().current()

        assertTrue(state.supported)
        assertNull(state.folderName)
        assertEquals(BackupListState.Hidden, state.backups)
        assertFalse(state.enabled)
        assertFalse(state.hasPassphrase)
        assertTrue(state.showPassphraseForm)
    }

    @Test
    fun `an unsupported device is reported`() {
        assertFalse(viewModel(supported = false).current().supported)
    }

    @Test
    fun `starts connected when a folder was chosen earlier`() {
        repository.connect("content://tree/1")

        assertEquals("Accounting-dev", viewModel().current().folderName)
    }

    @Test
    fun `connecting shows the folder name`() {
        val viewModel = viewModel()

        viewModel.connect("content://tree/1")

        assertEquals("Accounting-dev", viewModel.current().folderName)
        assertFalse(viewModel.current().connectFailed)
    }

    @Test
    fun `a folder that cannot be read is reported and not connected`() {
        folder.failing = true
        val viewModel = viewModel()

        viewModel.connect("content://tree/1")

        assertNull(viewModel.current().folderName)
        assertTrue(viewModel.current().connectFailed)

        viewModel.connectFailureShown()

        assertFalse(viewModel.current().connectFailed)
    }

    @Test
    fun `disconnecting forgets the folder and switches the daily backup off`() {
        configure()
        val viewModel = viewModel()
        viewModel.setEnabled(true)

        viewModel.disconnect()

        assertNull(viewModel.current().folderName)
        assertFalse(viewModel.current().enabled)
        assertFalse(store.enabled)
        assertEquals(1, scheduler.cancelled)
    }

    // --- passphrase ----------------------------------------------------------------------------

    @Test
    fun `a passphrase that is too short is refused`() {
        val viewModel = viewModel()

        viewModel.savePassphrase("short")

        assertTrue(viewModel.current().passphraseTooShort)
        assertFalse(viewModel.current().hasPassphrase)

        viewModel.passphraseEdited()

        assertFalse(viewModel.current().passphraseTooShort)
    }

    @Test
    fun `a good passphrase is saved and the form closes`() {
        val viewModel = viewModel()

        viewModel.savePassphrase("correct horse")

        assertEquals("correct horse", store.passphrase)
        assertTrue(viewModel.current().hasPassphrase)
        assertFalse(viewModel.current().showPassphraseForm)
    }

    @Test
    fun `the passphrase can be changed and the change cancelled`() {
        manager.setPassphrase("correct horse")
        val viewModel = viewModel()

        viewModel.startChangingPassphrase()
        assertTrue(viewModel.current().showPassphraseForm)

        viewModel.cancelChangingPassphrase()
        assertFalse(viewModel.current().showPassphraseForm)
        assertEquals("correct horse", store.passphrase)

        viewModel.startChangingPassphrase()
        viewModel.savePassphrase("another passphrase")
        assertEquals("another passphrase", store.passphrase)
        assertFalse(viewModel.current().showPassphraseForm)
    }

    // --- daily backup ----------------------------------------------------------------------------

    @Test
    fun `the daily backup cannot be switched on without a folder and a passphrase`() {
        val viewModel = viewModel()

        viewModel.setEnabled(true)
        assertFalse(viewModel.current().enabled)

        repository.connect("content://tree/1")
        viewModel.setEnabled(true)
        assertFalse(viewModel.current().enabled)
        assertFalse(store.enabled)
    }

    @Test
    fun `switching it on arms the alarm and off cancels it`() {
        configure()
        val viewModel = viewModel()

        viewModel.setEnabled(true)

        assertTrue(viewModel.current().enabled)
        assertEquals(1, scheduler.scheduled.size)

        viewModel.setEnabled(false)

        assertFalse(viewModel.current().enabled)
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun `backing up now uploads and shows the result and the time`() {
        configure()
        val viewModel = viewModel()

        viewModel.backUpNow()

        assertEquals(1, folder.files.size)
        assertTrue(viewModel.current().backupResult is CloudBackupOutcome.Success)
        assertEquals(now, viewModel.current().lastSuccessAt)
        assertFalse(viewModel.current().backingUp)

        viewModel.backupResultShown()

        assertNull(viewModel.current().backupResult)
    }

    @Test
    fun `backing up now while offline says so`() {
        configure()
        online = false
        val viewModel = viewModel()

        viewModel.backUpNow()

        assertEquals(CloudBackupOutcome.Offline, viewModel.current().backupResult)
        assertEquals(CloudBackupError.OFFLINE, viewModel.current().lastError)
        assertTrue(folder.files.isEmpty())
    }

    @Test
    fun `refreshing picks up what a background run recorded`() {
        configure()
        val viewModel = viewModel()
        store.lastSuccessAt = 99
        store.lastError = CloudBackupError.FOLDER

        viewModel.refresh()

        assertEquals(99L, viewModel.current().lastSuccessAt)
        assertEquals(CloudBackupError.FOLDER, viewModel.current().lastError)
    }

    @Test
    fun `app start catches up a due backup`() {
        configure()
        store.enabled = true

        viewModel().runCatchUpSync()

        assertEquals(1, folder.files.size)
    }

    // --- restore ---------------------------------------------------------------------------------

    @Test
    fun `loading the backups lists them newest first`() {
        folder.add("old.accbak", modifiedAt = 100)
        folder.add("new.accbak", modifiedAt = 200)
        val viewModel = viewModel()
        viewModel.connect("content://tree/1")

        viewModel.loadBackups()

        val backups = viewModel.current().backups as BackupListState.Loaded
        assertEquals(listOf("new.accbak", "old.accbak"), backups.files.map { it.name })
    }

    @Test
    fun `an empty folder gives an empty list`() {
        val viewModel = viewModel()
        viewModel.connect("content://tree/1")

        viewModel.loadBackups()

        assertEquals(BackupListState.Loaded(emptyList()), viewModel.current().backups)
    }

    @Test
    fun `an unreachable folder gives a failed list`() {
        val viewModel = viewModel()
        viewModel.connect("content://tree/1")
        folder.failing = true

        viewModel.loadBackups()

        assertEquals(BackupListState.Failed, viewModel.current().backups)
    }

    @Test
    fun `dismissing hides the list`() {
        val viewModel = viewModel()
        viewModel.connect("content://tree/1")
        viewModel.loadBackups()

        viewModel.dismissBackups()

        assertEquals(BackupListState.Hidden, viewModel.current().backups)
    }

    @Test
    fun `loading without a folder does nothing`() {
        val viewModel = viewModel()

        viewModel.loadBackups()

        assertEquals(BackupListState.Hidden, viewModel.current().backups)
    }

    private fun cloudFile(passphrase: String = "correct horse"): CloudBackupFile =
        folder.add("acc_x.accbak", BackupEnvelope.encode("{}", passphrase, iterations = 10))

    @Test
    fun `restoring opens the backup with the stored passphrase`() {
        configure()
        val viewModel = viewModel()

        viewModel.restore(cloudFile(), passphrase = null)

        assertEquals(CloudRestoreResult.Restored(RestoreResult.Success), viewModel.current().restoreResult)
        assertFalse(viewModel.current().restoring)

        viewModel.restoreResultShown()

        assertNull(viewModel.current().restoreResult)
    }

    @Test
    fun `restoring without a passphrase asks for one`() {
        repository.connect("content://tree/1")
        val viewModel = viewModel()

        viewModel.restore(cloudFile(), passphrase = null)

        assertEquals(CloudRestoreResult.NeedsPassphrase, viewModel.current().restoreResult)
    }

    @Test
    fun `a typed passphrase is used for that restore`() {
        repository.connect("content://tree/1")
        val viewModel = viewModel()

        viewModel.restore(cloudFile("typed one"), passphrase = "typed one")

        assertEquals(CloudRestoreResult.Restored(RestoreResult.Success), viewModel.current().restoreResult)
        assertNull(store.passphrase)
    }
}
