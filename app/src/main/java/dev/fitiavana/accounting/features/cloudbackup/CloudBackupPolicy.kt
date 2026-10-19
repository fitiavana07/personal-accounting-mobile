package dev.fitiavana.accounting.features.cloudbackup

/** The fixed rules of Cloud Backup, shared by the backup run and the screen that tells them to the user. */
object CloudBackupPolicy {
    const val BACKUP_EXTENSION = ".accbak"

    /** How many backups the cloud folder keeps; older ones are deleted. */
    const val KEPT_BACKUPS = 7

    const val MIN_PASSPHRASE_LENGTH = 8
}
