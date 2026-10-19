package dev.fitiavana.accounting.features.cloudbackup

/** Why the last cloud backup run did not produce a backup; the screen turns it into words. */
enum class CloudBackupError {
    /** No connection: the run was skipped and will be retried. Not counted as a failure. */
    OFFLINE,

    /** No folder or no passphrase. Not retried: the user has to fix the setup. */
    NOT_CONFIGURED,

    /** The folder could not be written: access revoked, deleted, provider error. */
    FOLDER,

    /** Anything else, such as the export failing. */
    UNEXPECTED
}
