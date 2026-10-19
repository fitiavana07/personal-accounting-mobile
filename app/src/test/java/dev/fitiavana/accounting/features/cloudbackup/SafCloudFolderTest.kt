package dev.fitiavana.accounting.features.cloudbackup

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.os.BundleCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * A stand-in for the Drive documents provider: only answers the two queries the folder makes, told
 * apart by the uri (the children of the tree root versus the tree root itself).
 */
class FakeDocumentsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        if (failing) return null
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE
        )
        val rows = if (uri.pathSegments.last() == "children") {
            children
        } else {
            listOf(arrayOf("root", "Accounting-dev", Document.MIME_TYPE_DIR, null, null))
        }
        // Like a real provider, answer with the columns asked for, in the order asked for.
        val asked = projection ?: columns
        return MatrixCursor(asked).apply {
            rows.forEach { row -> addRow(asked.map { row[columns.indexOf(it)] }) }
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val target = extras?.let { BundleCompat.getParcelable(it, EXTRA_URI, Uri::class.java) }
        return when (method) {
            "android:createDocument" -> {
                if (failing) throw IllegalStateException("revoked")
                val name = extras!!.getString(Document.COLUMN_DISPLAY_NAME)!!
                val id = "new-$name"
                val file = File.createTempFile("doc", ".bin").also { it.deleteOnExit() }
                documents[id] = file
                created += name
                Bundle().apply { putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(target, id)) }
            }
            "android:deleteDocument" -> {
                documents.remove(DocumentsContract.getDocumentId(target))
                deleted += DocumentsContract.getDocumentId(target)
                Bundle()
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (failing) throw SecurityException("access revoked")
        val file = documents.getValue(DocumentsContract.getDocumentId(uri))
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        /** DocumentsContract.EXTRA_URI, which the SDK keeps hidden. */
        const val EXTRA_URI = "uri"
        var failing = false
        val documents = mutableMapOf<String, File>()
        val created = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var children: List<Array<Any?>> = emptyList()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class SafCloudFolderTest {

    private val authority = "com.example.documents"
    private val treeUri: Uri = Uri.parse("content://$authority/tree/root")

    @Before
    fun setUp() {
        FakeDocumentsProvider.failing = false
        FakeDocumentsProvider.documents.clear()
        FakeDocumentsProvider.created.clear()
        FakeDocumentsProvider.deleted.clear()
        FakeDocumentsProvider.children = emptyList()
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(authority)
    }

    private fun folder() =
        SafCloudFolder(ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver, treeUri)

    @Test
    fun `the display name is the name of the folder`() {
        assertEquals("Accounting-dev", folder().displayName())
    }

    @Test
    fun `lists the files in the folder, not its sub folders`() {
        FakeDocumentsProvider.children = listOf(
            arrayOf("1", "a.accbak", "application/octet-stream", 1_000L, 2_048L),
            arrayOf("2", "sub", Document.MIME_TYPE_DIR, 2_000L, null),
            arrayOf("3", "b.txt", "text/plain", null, null)
        )

        val files = folder().list()

        assertEquals(listOf("a.accbak", "b.txt"), files.map { it.name })
        assertEquals(CloudBackupFile(id = "1", name = "a.accbak", modifiedAt = 1_000L, sizeBytes = 2_048L), files[0])
        assertEquals(0L, files[1].modifiedAt)
        assertEquals(0L, files[1].sizeBytes)
    }

    @Test(expected = IOException::class)
    fun `an unreachable provider fails the listing`() {
        FakeDocumentsProvider.failing = true

        folder().list()
    }

    @Test(expected = IOException::class)
    fun `an unreachable provider fails the display name`() {
        FakeDocumentsProvider.failing = true

        folder().displayName()
    }

    @Test
    fun `a created file can be read back and deleted`() {
        val file = folder().create("a.accbak", byteArrayOf(1, 2, 3))

        assertEquals("a.accbak", file.name)
        assertEquals(3L, file.sizeBytes)
        assertArrayEquals(byteArrayOf(1, 2, 3), folder().read(file.id))

        folder().delete(file.id)

        assertEquals(listOf(file.id), FakeDocumentsProvider.deleted)
    }

    @Test(expected = IOException::class)
    fun `a revoked folder fails the creation with an IOException`() {
        FakeDocumentsProvider.failing = true

        folder().create("a.accbak", byteArrayOf(1))
    }
}
