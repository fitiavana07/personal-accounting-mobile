package dev.fitiavana.accounting.features.cloudbackup

import java.io.IOException

/** A file found in the cloud folder. */
data class CloudBackupFile(
    /** The provider's id for the document; opaque. */
    val id: String,
    val name: String,
    /** Epoch millis, 0 when the provider does not say. */
    val modifiedAt: Long,
    /** Bytes, 0 when the provider does not say. */
    val sizeBytes: Long
)

/** The folder the user picked in their cloud storage. Every call may fail with an [IOException] (offline, access revoked...). */
interface CloudFolder {
    /** The folder's own name, as the provider shows it. */
    fun displayName(): String

    /** The files directly in the folder (sub folders excluded), in no particular order. */
    fun list(): List<CloudBackupFile>

    /** Writes a new file; a file that could not be written completely is removed again. */
    fun create(name: String, bytes: ByteArray): CloudBackupFile

    fun read(id: String): ByteArray

    fun delete(id: String)
}
