package dev.fitiavana.accounting.features.backup

import android.content.SharedPreferences

/** Remembers whether automatic daily backups are on and when the last one succeeded. */
interface BackupPrefsStore {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun lastBackupAt(): Long
    fun setLastBackupAt(millis: Long)
}

class SharedPreferencesBackupPrefsStore(private val prefs: SharedPreferences) : BackupPrefsStore {

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    override fun lastBackupAt(): Long = prefs.getLong(KEY_LAST_BACKUP_AT, 0L)

    override fun setLastBackupAt(millis: Long) {
        // commit(): the receiver may be killed right after finishing, so don't rely on the async write.
        prefs.edit().putLong(KEY_LAST_BACKUP_AT, millis).commit()
    }

    companion object {
        const val PREFS_NAME = "auto_backup"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_BACKUP_AT = "last_backup_at"
    }
}
