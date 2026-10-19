package dev.fitiavana.accounting.features.cloudbackup

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.annotation.RequiresApi
import java.io.IOException

/** Whether this Android version can pick a cloud folder and work inside it (see [SafCloudFolder]). */
fun isCloudBackupSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= 23

/** Opens the folder behind a stored tree uri; below API 23 nothing can have been stored, so any use fails. */
fun openCloudFolder(resolver: ContentResolver, uri: String): CloudFolder =
    if (Build.VERSION.SDK_INT >= 23) SafCloudFolder(resolver, Uri.parse(uri)) else UnavailableCloudFolder

private object UnavailableCloudFolder : CloudFolder {
    private const val MESSAGE = "Cloud Backup needs Android 6.0 or newer"
    override fun displayName(): String = throw IOException(MESSAGE)
    override fun list(): List<CloudBackupFile> = throw IOException(MESSAGE)
    override fun create(name: String, bytes: ByteArray): CloudBackupFile = throw IOException(MESSAGE)
    override fun read(id: String): ByteArray = throw IOException(MESSAGE)
    override fun delete(id: String) = throw IOException(MESSAGE)
}

/**
 * A folder picked with the Storage Access Framework ([DocumentsContract.isTreeUri]), typically in Google Drive.
 * Choosing a folder and working inside it is API 21+, hence the API 23 floor of the whole feature.
 */
@RequiresApi(23)
class SafCloudFolder(private val resolver: ContentResolver, private val treeUri: Uri) : CloudFolder {

    private val rootId: String get() = DocumentsContract.getTreeDocumentId(treeUri)

    private val rootDocumentUri: Uri get() = documentUri(rootId)

    private fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    override fun displayName(): String =
        query(rootDocumentUri, arrayOf(Document.COLUMN_DISPLAY_NAME)) { it.getString(0) }.firstOrNull()
            ?: throw IOException("The folder has no name")

    override fun list(): List<CloudBackupFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE
        )
        return query(children, columns) {
            val isFolder = it.getString(2) == Document.MIME_TYPE_DIR
            if (isFolder) null else CloudBackupFile(
                id = it.getString(0),
                name = it.getString(1),
                modifiedAt = if (it.isNull(3)) 0 else it.getLong(3),
                sizeBytes = if (it.isNull(4)) 0 else it.getLong(4)
            )
        }.filterNotNull()
    }

    override fun create(name: String, bytes: ByteArray): CloudBackupFile {
        val created = guarded("The file could not be created") {
            DocumentsContract.createDocument(resolver, rootDocumentUri, BACKUP_MIME_TYPE, name)
        } ?: throw IOException("The file could not be created")
        try {
            guarded("The file could not be written") {
                (resolver.openOutputStream(created) ?: throw IOException("The file could not be opened")).use { it.write(bytes) }
            }
        } catch (e: IOException) {
            // Never leave half a backup behind: it would look like the newest one.
            runCatching { DocumentsContract.deleteDocument(resolver, created) }
            throw e
        }
        return CloudBackupFile(
            id = DocumentsContract.getDocumentId(created),
            name = name,
            modifiedAt = System.currentTimeMillis(),
            sizeBytes = bytes.size.toLong()
        )
    }

    override fun read(id: String): ByteArray =
        guarded("The file could not be read") {
            (resolver.openInputStream(documentUri(id)) ?: throw IOException("The file could not be opened")).use { it.readBytes() }
        }

    override fun delete(id: String) {
        val deleted = guarded("The file could not be deleted") { DocumentsContract.deleteDocument(resolver, documentUri(id)) }
        if (!deleted) throw IOException("The file could not be deleted")
    }

    private fun <T> query(uri: Uri, columns: Array<String>, read: (Cursor) -> T): List<T> {
        val cursor = guarded("The folder could not be read") { resolver.query(uri, columns, null, null, null) }
            ?: throw IOException("The folder could not be read")
        return cursor.use {
            val rows = mutableListOf<T>()
            while (it.moveToNext()) rows += read(it)
            rows
        }
    }

    /** Providers signal an unreachable or revoked folder with assorted runtime exceptions; callers expect an [IOException]. */
    private fun <T> guarded(message: String, block: () -> T): T =
        try {
            block()
        } catch (e: RuntimeException) {
            throw IOException(message, e)
        }

    private companion object {
        /** A type no provider knows, so none renames the file or adds an extension to the name we give. */
        const val BACKUP_MIME_TYPE = "application/octet-stream"
    }
}
