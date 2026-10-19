package dev.fitiavana.accounting.features.cloudbackup

import java.io.IOException

/** The chosen cloud folder and the backups found in it. [folderFor] opens the folder behind a stored uri. */
class CloudBackupRepository(
    private val store: CloudBackupStore,
    private val folderFor: (String) -> CloudFolder
) {
    fun isConnected(): Boolean = store.folderUri != null

    fun folderName(): String? = store.folderName

    /** Remembers the folder after checking it can be read; returns its name. Fails with an [IOException] otherwise. */
    fun connect(uri: String): String {
        val name = folderFor(uri).displayName()
        store.setFolder(uri, name)
        return name
    }

    fun disconnect() = store.clearFolder()

    /** Writes a new file into the connected folder. */
    fun upload(name: String, bytes: ByteArray): CloudBackupFile = connectedFolder().create(name, bytes)

    fun read(file: CloudBackupFile): ByteArray = connectedFolder().read(file.id)

    /**
     * Deletes all but the newest [keep] `.accbak` files whose name starts with [prefix] (names carry the
     * date, so the newest sort last). Files of other builds, other types or other names are never touched.
     */
    fun prune(prefix: String, keep: Int) {
        val folder = connectedFolder()
        folder.list()
            .filter { it.name.startsWith(prefix) && it.name.endsWith(CloudBackupPolicy.BACKUP_EXTENSION) }
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { folder.delete(it.id) }
    }

    /** The `.accbak` files in the folder, newest first. Fails with an [IOException] when the folder cannot be reached. */
    fun listBackups(): List<CloudBackupFile> {
        return connectedFolder().list()
            .filter { it.name.endsWith(CloudBackupPolicy.BACKUP_EXTENSION, ignoreCase = true) }
            .sortedWith(compareByDescending<CloudBackupFile> { it.modifiedAt }.thenByDescending { it.name })
    }

    private fun connectedFolder(): CloudFolder = folderFor(checkNotNull(store.folderUri) { "No cloud folder is connected" })
}
