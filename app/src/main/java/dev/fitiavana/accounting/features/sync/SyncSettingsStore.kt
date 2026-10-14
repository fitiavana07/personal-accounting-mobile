package dev.fitiavana.accounting.features.sync

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Device-local sync settings, kept in preferences: they are never synced to other devices, nor
 * included in backups (see the backup rules), since a restored id would be shared by two phones.
 */
class SyncSettingsStore(
    private val prefs: SharedPreferences,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutablePassphrase = MutableStateFlow(prefs.getString(PASSPHRASE, null))
    private val mutableAutoSync = MutableStateFlow(prefs.getBoolean(AUTO_SYNC, true))

    val passphrase: StateFlow<String?> = mutablePassphrase

    val autoSync: StateFlow<Boolean> = mutableAutoSync

    /** This installation's id, generated on first use and then kept for good. */
    @Synchronized
    fun deviceId(): String {
        prefs.getString(DEVICE_ID, null)?.let { return it }
        return newId().also { prefs.edit().putString(DEVICE_ID, it).commit() }
    }

    /** A blank passphrase counts as none. */
    fun setPassphrase(value: String?) {
        val passphrase = value?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().apply { if (passphrase == null) remove(PASSPHRASE) else putString(PASSPHRASE, passphrase) }.commit()
        mutablePassphrase.value = passphrase
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean(AUTO_SYNC, enabled).commit()
        mutableAutoSync.value = enabled
    }

    companion object {
        const val PREFS_NAME = "sync"
        private const val DEVICE_ID = "device_id"
        private const val PASSPHRASE = "passphrase"
        private const val AUTO_SYNC = "auto_sync"
    }
}
