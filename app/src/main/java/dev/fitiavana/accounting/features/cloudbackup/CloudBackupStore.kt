package dev.fitiavana.accounting.features.cloudbackup

import android.content.SharedPreferences

/**
 * What Cloud Backup remembers on this installation: the chosen folder, the passphrase, and how the last
 * runs went. Local only (never synced or restored from a backup), and per build: debug and release
 * each hold their own.
 */
interface CloudBackupStore {
    /** The SAF tree uri of the chosen folder, or null while disconnected. */
    val folderUri: String?

    /** The folder's name, kept to show it without reaching the provider. */
    val folderName: String?

    fun setFolder(uri: String, name: String)
    fun clearFolder()

    var enabled: Boolean

    /** When the daily backup was switched on: the start of the "no backup for days" count. */
    var enabledAt: Long

    /** The dedicated passphrase; null when none is set or it can no longer be read back. */
    var passphrase: String?

    var lastSuccessAt: Long
    var lastError: CloudBackupError?

    /** Runs that failed since the last success; being offline is not a failure. */
    var consecutiveFailures: Int

    /** The user was told about the current streak of failures. */
    var failureNotified: Boolean
}

class SharedPreferencesCloudBackupStore(
    private val prefs: SharedPreferences,
    private val secrets: SecretCipher
) : CloudBackupStore {
    override val folderUri: String? get() = prefs.getString(KEY_FOLDER_URI, null)
    override val folderName: String? get() = prefs.getString(KEY_FOLDER_NAME, null)

    override fun setFolder(uri: String, name: String) {
        edit { putString(KEY_FOLDER_URI, uri).putString(KEY_FOLDER_NAME, name) }
    }

    override fun clearFolder() {
        edit { remove(KEY_FOLDER_URI).remove(KEY_FOLDER_NAME) }
    }

    override var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = edit { putBoolean(KEY_ENABLED, value) }

    override var enabledAt: Long
        get() = prefs.getLong(KEY_ENABLED_AT, 0L)
        set(value) = edit { putLong(KEY_ENABLED_AT, value) }

    override var passphrase: String?
        get() = prefs.getString(KEY_PASSPHRASE, null)?.let(secrets::open)
        set(value) = edit { if (value == null) remove(KEY_PASSPHRASE) else putString(KEY_PASSPHRASE, secrets.seal(value)) }

    override var lastSuccessAt: Long
        get() = prefs.getLong(KEY_LAST_SUCCESS_AT, 0L)
        set(value) = edit { putLong(KEY_LAST_SUCCESS_AT, value) }

    override var lastError: CloudBackupError?
        get() = prefs.getString(KEY_LAST_ERROR, null)?.let { name -> CloudBackupError.values().firstOrNull { it.name == name } }
        set(value) = edit { if (value == null) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, value.name) }

    override var consecutiveFailures: Int
        get() = prefs.getInt(KEY_CONSECUTIVE_FAILURES, 0)
        set(value) = edit { putInt(KEY_CONSECUTIVE_FAILURES, value) }

    override var failureNotified: Boolean
        get() = prefs.getBoolean(KEY_FAILURE_NOTIFIED, false)
        set(value) = edit { putBoolean(KEY_FAILURE_NOTIFIED, value) }

    // commit(): the alarm receiver may be killed right after finishing, so don't rely on the async write.
    private fun edit(change: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(change).commit()
    }

    companion object {
        const val PREFS_NAME = "cloud_backup"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_FOLDER_NAME = "folder_name"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ENABLED_AT = "enabled_at"
        private const val KEY_PASSPHRASE = "passphrase"
        private const val KEY_LAST_SUCCESS_AT = "last_success_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_CONSECUTIVE_FAILURES = "consecutive_failures"
        private const val KEY_FAILURE_NOTIFIED = "failure_notified"
    }
}
