package dev.fitiavana.accounting.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.backup.AutoBackupManager

/** What the Auto-backup dialog shows; [lastBackupAt] and [folderPath] are null when unknown. */
data class AutoBackupStatus(val enabled: Boolean, val lastBackupAt: Long?, val folderPath: String?)

class AutoBackupViewModel(private val manager: AutoBackupManager) : ViewModel() {

    /** Cheap, but touches the filesystem for the folder, so call it from a click, not a draw loop. */
    fun status(): AutoBackupStatus = AutoBackupStatus(
        enabled = manager.isEnabled(),
        lastBackupAt = manager.lastBackupAt().takeIf { it > 0L },
        folderPath = manager.backupFolder()?.path
    )

    fun setEnabled(enabled: Boolean) = manager.setEnabled(enabled)

    /** Catch-up on app start for alarms that never fired; runs the backup off the main thread. */
    fun runCatchUp() {
        Thread { runCatchUpSync() }.start()
    }

    internal fun runCatchUpSync() {
        manager.runIfDue()
    }
}

class AutoBackupViewModelFactory(private val manager: AutoBackupManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AutoBackupViewModel(manager) as T
    }
}
