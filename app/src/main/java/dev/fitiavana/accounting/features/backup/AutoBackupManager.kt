package dev.fitiavana.accounting.features.backup

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Arms (and disarms) the single alarm that triggers the next automatic backup. */
interface AutoBackupScheduler {
    fun scheduleAt(triggerAtMillis: Long)
    fun cancel()
}

/**
 * Writes a JSON backup into [backupDir] about once a day, keeping the newest [MAX_BACKUPS].
 *
 * It is driven from three places, so each entry point re-arms the alarm: the alarm receiver
 * ([onAlarm]), the boot receiver ([ensureScheduled]) and app start ([runIfDue], a catch-up for
 * missed alarms). Failures never throw; they are retried after [RETRY_MILLIS].
 */
class AutoBackupManager(
    private val export: () -> String,
    private val backupDir: () -> File?,
    private val store: BackupPrefsStore,
    private val scheduler: AutoBackupScheduler,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** True when auto-backup is on and no backup was made in the last 24 hours. */
    fun isDue(): Boolean {
        if (!store.isEnabled()) return false
        val last = store.lastBackupAt()
        return last == 0L || clock() - last >= INTERVAL_MILLIS
    }

    /** App-start catch-up: backs up if due, otherwise just makes sure the alarm is armed. */
    fun runIfDue(): File? {
        if (!store.isEnabled()) return null
        if (isDue()) return runBackup()
        ensureScheduled()
        return null
    }

    /** The alarm fired: back up now (if still enabled) and arm the next one. */
    fun onAlarm(): File? = if (store.isEnabled()) runBackup() else null

    /** Writes a backup now and arms the next alarm; returns null (and retries later) on failure. */
    fun runBackup(): File? {
        val now = clock()
        val file = try {
            write(now)
        } catch (e: Exception) {
            null
        }
        if (file == null) {
            scheduler.scheduleAt(now + RETRY_MILLIS)
            return null
        }
        store.setLastBackupAt(now)
        prune()
        scheduler.scheduleAt(now + INTERVAL_MILLIS)
        return file
    }

    /** Arms the alarm for 24h after the last backup, or soon if that is already overdue. */
    fun ensureScheduled() {
        if (!store.isEnabled()) return
        val now = clock()
        scheduler.scheduleAt(maxOf(store.lastBackupAt() + INTERVAL_MILLIS, now + OVERDUE_DELAY_MILLIS))
    }

    fun setEnabled(enabled: Boolean) {
        store.setEnabled(enabled)
        if (enabled) ensureScheduled() else scheduler.cancel()
    }

    fun isEnabled(): Boolean = store.isEnabled()

    fun lastBackupAt(): Long = store.lastBackupAt()

    fun backupFolder(): File? = backupDir()

    fun latestBackup(): File? = autoBackupFiles().lastOrNull()

    private fun write(now: Long): File {
        val dir = backupDir() ?: throw IllegalStateException("No backup directory")
        if (!dir.isDirectory && !dir.mkdirs()) throw IllegalStateException("Cannot create $dir")
        val name = "$FILE_PREFIX${fileDateFormat().format(Date(now))}$FILE_SUFFIX"
        val target = File(dir, name)
        val temp = File(dir, "$name.tmp")
        temp.writeText(export())
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IllegalStateException("Cannot write $target")
        }
        return target
    }

    private fun prune() {
        autoBackupFiles().dropLast(MAX_BACKUPS).forEach { it.delete() }
    }

    /** Auto-backup files only (never manual ones), oldest first; names sort chronologically. */
    private fun autoBackupFiles(): List<File> =
        backupDir()?.listFiles { file ->
            file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        }.orEmpty().sortedBy { it.name }

    private fun fileDateFormat() = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)

    companion object {
        const val INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val RETRY_MILLIS = 60L * 60 * 1000
        const val OVERDUE_DELAY_MILLIS = 60L * 1000
        const val MAX_BACKUPS = 7
        const val FILE_PREFIX = "accounting_auto_backup_"
        private const val FILE_SUFFIX = ".json"
    }
}
