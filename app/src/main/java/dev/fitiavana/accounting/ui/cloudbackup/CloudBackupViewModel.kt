package dev.fitiavana.accounting.ui.cloudbackup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupError
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupFile
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupManager
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupOutcome
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupPolicy
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupRepository
import dev.fitiavana.accounting.features.cloudbackup.CloudRestoreResult
import dev.fitiavana.accounting.features.cloudbackup.isCloudBackupSupported
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The list of cloud backups the user can restore from, as the picker shows it. */
sealed class BackupListState {
    object Hidden : BackupListState()
    object Loading : BackupListState()
    data class Loaded(val files: List<CloudBackupFile>) : BackupListState()
    object Failed : BackupListState()
}

/** Everything the Cloud Backup screen shows. */
data class CloudBackupScreenState(
    /** False below Android 6.0, where the whole feature is unavailable. */
    val supported: Boolean,
    /** The connected folder's name, null while disconnected. */
    val folderName: String?,
    val backups: BackupListState = BackupListState.Hidden,
    /** The last folder the user picked could not be read; shown once. */
    val connectFailed: Boolean = false,
    val enabled: Boolean = false,
    val hasPassphrase: Boolean = false,
    /** The passphrase form: shown when there is no passphrase, or while the user is changing it. */
    val showPassphraseForm: Boolean = true,
    val passphraseTooShort: Boolean = false,
    val lastSuccessAt: Long? = null,
    val lastError: CloudBackupError? = null,
    val backingUp: Boolean = false,
    /** How the last "Back up now" went; shown once. */
    val backupResult: CloudBackupOutcome? = null,
    val restoring: Boolean = false,
    /** How the last restore went; shown once. */
    val restoreResult: CloudRestoreResult? = null
)

/** The Cloud Backup screen's logic: the folder, the passphrase, the daily backup, and restoring. */
class CloudBackupViewModel(
    private val repository: CloudBackupRepository,
    private val manager: CloudBackupManager,
    supported: Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val io: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private var changingPassphrase = false

    private val mutableState = MutableLiveData(
        CloudBackupScreenState(supported = supported, folderName = repository.folderName()).withStatus()
    )

    val state: LiveData<CloudBackupScreenState> = mutableState

    private fun update(change: (CloudBackupScreenState) -> CloudBackupScreenState) {
        mutableState.value = change(mutableState.value!!)
    }

    /** What the manager and the stored passphrase say now: a background run may have changed it. */
    private fun CloudBackupScreenState.withStatus(): CloudBackupScreenState {
        val status = manager.status()
        return copy(
            enabled = status.enabled,
            hasPassphrase = status.hasPassphrase,
            showPassphraseForm = !status.hasPassphrase || changingPassphrase,
            lastSuccessAt = status.lastSuccessAt,
            lastError = status.lastError
        )
    }

    /** Re-reads what a background backup may have recorded since the screen was last shown. */
    fun refresh() = update { it.withStatus() }

    // --- folder ------------------------------------------------------------------------------

    /** Remembers the folder the user picked, once the provider confirms it can be read. */
    fun connect(uri: String) {
        scope.launch {
            val name = withContext(io) { runCatching { repository.connect(uri) } }.getOrNull()
            update { it.copy(folderName = name ?: it.folderName, connectFailed = name == null) }
        }
    }

    fun connectFailureShown() = update { it.copy(connectFailed = false) }

    /** Forgets the folder; with nowhere to back up to, the daily backup is switched off too. */
    fun disconnect() {
        repository.disconnect()
        manager.setEnabled(false)
        update { it.copy(folderName = null, backups = BackupListState.Hidden).withStatus() }
    }

    // --- passphrase --------------------------------------------------------------------------

    fun savePassphrase(passphrase: String) {
        if (passphrase.length < CloudBackupPolicy.MIN_PASSPHRASE_LENGTH) {
            update { it.copy(passphraseTooShort = true) }
            return
        }
        manager.setPassphrase(passphrase)
        changingPassphrase = false
        update { it.copy(passphraseTooShort = false).withStatus() }
    }

    fun passphraseEdited() = update { it.copy(passphraseTooShort = false) }

    fun startChangingPassphrase() {
        changingPassphrase = true
        update { it.withStatus() }
    }

    fun cancelChangingPassphrase() {
        changingPassphrase = false
        update { it.copy(passphraseTooShort = false).withStatus() }
    }

    // --- daily backup ------------------------------------------------------------------------

    /** Switching on needs a folder and a passphrase; without them nothing changes. */
    fun setEnabled(enabled: Boolean) {
        if (enabled && !(repository.isConnected() && manager.status().hasPassphrase)) return
        manager.setEnabled(enabled)
        update { it.withStatus() }
    }

    fun backUpNow() {
        update { it.copy(backingUp = true) }
        scope.launch {
            val outcome = withContext(io) { manager.runBackup() }
            update { it.copy(backingUp = false, backupResult = outcome).withStatus() }
        }
    }

    fun backupResultShown() = update { it.copy(backupResult = null) }

    /** Catch-up on app start for alarms that never fired; runs the backup off the main thread. */
    fun runCatchUp() {
        Thread { runCatchUpSync() }.start()
    }

    internal fun runCatchUpSync() {
        manager.runIfDue()
    }

    // --- restore -----------------------------------------------------------------------------

    fun loadBackups() {
        if (!repository.isConnected()) return
        update { it.copy(backups = BackupListState.Loading) }
        scope.launch {
            val files = withContext(io) { runCatching { manager.listBackups() } }.getOrNull()
            update { it.copy(backups = if (files == null) BackupListState.Failed else BackupListState.Loaded(files)) }
        }
    }

    fun dismissBackups() = update { it.copy(backups = BackupListState.Hidden) }

    /** Restores [file] with the stored passphrase, or with [passphrase] when the user typed one. */
    fun restore(file: CloudBackupFile, passphrase: String?) {
        update { it.copy(restoring = true) }
        scope.launch {
            val result = withContext(io) { manager.restore(file, passphrase) }
            update { it.copy(restoring = false, restoreResult = result) }
        }
    }

    fun restoreResultShown() = update { it.copy(restoreResult = null) }

    override fun onCleared() = scope.cancel()
}

class CloudBackupViewModelFactory(
    private val repository: CloudBackupRepository,
    private val manager: CloudBackupManager,
    private val supported: Boolean = isCloudBackupSupported()
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CloudBackupViewModel(repository, manager, supported) as T
    }
}
