package dev.fitiavana.accounting.features.cloudbackup

import dev.fitiavana.accounting.features.backup.RestoreResult
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Arms (and disarms) the single alarm that triggers the next cloud backup. */
interface CloudBackupScheduler {
    fun scheduleAt(triggerAtMillis: Long)
    fun cancel()
}

fun interface NetworkChecker {
    fun isOnline(): Boolean
}

/** Tells the user, outside the app, that cloud backups keep failing. */
interface CloudBackupNotifier {
    fun notifyFailing()
    fun clear()
}

/** What the Cloud Backup screen shows about the daily backup. */
data class CloudBackupStatus(
    val enabled: Boolean,
    val hasPassphrase: Boolean,
    /** Null until a backup has succeeded. */
    val lastSuccessAt: Long?,
    val lastError: CloudBackupError?
)

sealed class CloudBackupOutcome {
    data class Success(val file: CloudBackupFile) : CloudBackupOutcome()
    object Offline : CloudBackupOutcome()
    object NotConfigured : CloudBackupOutcome()
    object Failed : CloudBackupOutcome()
}

sealed class CloudRestoreResult {
    /** The backup was opened; [result] is what the database restore said. */
    data class Restored(val result: RestoreResult) : CloudRestoreResult()

    /** No passphrase is stored and none was given. */
    object NeedsPassphrase : CloudRestoreResult()
    object WrongPassphrase : CloudRestoreResult()
    object NotABackup : CloudRestoreResult()

    /** Written by a newer app. */
    object UnsupportedFormat : CloudRestoreResult()
    object Unreachable : CloudRestoreResult()
}

/**
 * Backs the data up, encrypted, into the cloud folder about once a day, keeping the newest
 * [CloudBackupPolicy.KEPT_BACKUPS]; and restores from it. Isolated from the local auto-backup: it has its
 * own alarm and state, with the same cadence ([INTERVAL_MILLIS], retry after [RETRY_MILLIS], catch-up at app start).
 *
 * Being offline skips the run and retries later without counting as a failure; real failures are counted,
 * and the user is notified (once per streak) after [NOTIFY_AFTER_FAILURES] of them or when no backup
 * succeeded for [NOTIFY_AFTER_MILLIS]. Nothing here throws.
 */
class CloudBackupManager(
    private val export: () -> String,
    private val repository: CloudBackupRepository,
    private val store: CloudBackupStore,
    private val scheduler: CloudBackupScheduler,
    private val network: NetworkChecker,
    private val notifier: CloudBackupNotifier,
    private val restore: (String) -> RestoreResult,
    /** Starts every file name; differs per build so debug and release never prune each other's files. */
    private val filePrefix: String,
    private val iterations: Int = BackupEnvelope.DEFAULT_ITERATIONS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    fun isDue(): Boolean {
        if (!store.enabled) return false
        val last = store.lastSuccessAt
        return last == 0L || clock() - last >= INTERVAL_MILLIS
    }

    /** App-start catch-up: backs up if due, otherwise just makes sure the alarm is armed. */
    fun runIfDue(): CloudBackupOutcome? {
        if (!store.enabled) return null
        if (isDue()) return runBackup()
        ensureScheduled()
        return null
    }

    /** The alarm fired: back up now (if still enabled) and arm the next one. */
    fun onAlarm(): CloudBackupOutcome? = if (store.enabled) runBackup() else null

    /** Backs up now and arms the next alarm, whatever happens. */
    fun runBackup(): CloudBackupOutcome {
        val now = clock()
        val passphrase = store.passphrase
        if (!repository.isConnected() || passphrase == null) {
            store.lastError = CloudBackupError.NOT_CONFIGURED
            return CloudBackupOutcome.NotConfigured
        }
        if (!network.isOnline()) {
            store.lastError = CloudBackupError.OFFLINE
            scheduler.scheduleAt(now + RETRY_MILLIS)
            notifyIfFailingLong(now)
            return CloudBackupOutcome.Offline
        }
        val file = try {
            val bytes = BackupEnvelope.encode(export(), passphrase, iterations)
            repository.upload(fileName(now), bytes)
        } catch (e: IOException) {
            return failed(now, CloudBackupError.FOLDER)
        } catch (e: Exception) {
            return failed(now, CloudBackupError.UNEXPECTED)
        }
        succeeded(now)
        return CloudBackupOutcome.Success(file)
    }

    /** Arms the alarm for 24h after the last backup, or soon if that is already overdue. */
    fun ensureScheduled() {
        if (!store.enabled) return
        scheduler.scheduleAt(maxOf(store.lastSuccessAt + INTERVAL_MILLIS, clock() + OVERDUE_DELAY_MILLIS))
    }

    fun setEnabled(enabled: Boolean) {
        store.enabled = enabled
        if (enabled) {
            store.enabledAt = clock()
            ensureScheduled()
        } else {
            scheduler.cancel()
        }
    }

    fun status() = CloudBackupStatus(
        enabled = store.enabled,
        hasPassphrase = store.passphrase != null,
        lastSuccessAt = store.lastSuccessAt.takeIf { it > 0L },
        lastError = store.lastError
    )

    /** The dedicated passphrase that encrypts every backup and is used, unasked, to restore them. */
    fun setPassphrase(passphrase: String) {
        store.passphrase = passphrase
    }

    /** The backups in the folder, newest first; fails with an [IOException] when it cannot be reached. */
    fun listBackups(): List<CloudBackupFile> = repository.listBackups()

    /**
     * Restores [file]. The stored passphrase is used unless [passphrase] is given (typed by the user, for
     * one restore only). The database is only touched once the file has been opened.
     */
    fun restore(file: CloudBackupFile, passphrase: String?): CloudRestoreResult {
        val secret = passphrase ?: store.passphrase ?: return CloudRestoreResult.NeedsPassphrase
        val json = try {
            BackupEnvelope.decode(repository.read(file), secret)
        } catch (e: BackupEnvelopeException) {
            return when (e.reason) {
                BackupEnvelopeException.Reason.NOT_AN_ENVELOPE -> CloudRestoreResult.NotABackup
                BackupEnvelopeException.Reason.UNSUPPORTED_VERSION -> CloudRestoreResult.UnsupportedFormat
                BackupEnvelopeException.Reason.WRONG_PASSPHRASE_OR_DAMAGED -> CloudRestoreResult.WrongPassphrase
            }
        } catch (e: IOException) {
            return CloudRestoreResult.Unreachable
        }
        return CloudRestoreResult.Restored(restore(json))
    }

    private fun succeeded(now: Long) {
        val wasNotified = store.failureNotified
        store.lastSuccessAt = now
        store.lastError = null
        store.consecutiveFailures = 0
        store.failureNotified = false
        if (wasNotified) notifier.clear()
        // A pruning problem must not turn a backup that is safely in the cloud into a failure.
        runCatching { repository.prune(filePrefix, CloudBackupPolicy.KEPT_BACKUPS) }
        scheduler.scheduleAt(now + INTERVAL_MILLIS)
    }

    private fun failed(now: Long, error: CloudBackupError): CloudBackupOutcome {
        store.lastError = error
        store.consecutiveFailures += 1
        scheduler.scheduleAt(now + RETRY_MILLIS)
        notifyIfFailingLong(now)
        return CloudBackupOutcome.Failed
    }

    private fun notifyIfFailingLong(now: Long) {
        if (store.failureNotified) return
        val since = maxOf(store.lastSuccessAt, store.enabledAt)
        if (store.consecutiveFailures >= NOTIFY_AFTER_FAILURES || now - since > NOTIFY_AFTER_MILLIS) {
            notifier.notifyFailing()
            store.failureNotified = true
        }
    }

    private fun fileName(now: Long) =
        "$filePrefix${SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date(now))}${CloudBackupPolicy.BACKUP_EXTENSION}"

    companion object {
        const val INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val RETRY_MILLIS = 60L * 60 * 1000
        const val OVERDUE_DELAY_MILLIS = 60L * 1000
        const val NOTIFY_AFTER_FAILURES = 3
        const val NOTIFY_AFTER_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}
