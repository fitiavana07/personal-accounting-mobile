package dev.fitiavana.accounting.features.cloudbackup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CloudBackupRepositoryTest {

    private val store = FakeCloudBackupStore()
    private val folder = FakeCloudFolder()
    private val requestedUris = mutableListOf<String>()
    private val repository = CloudBackupRepository(store) { uri -> requestedUris += uri; folder }

    private fun setFiles(vararg files: CloudBackupFile) {
        folder.files.clear()
        folder.files += files
    }

    private fun file(name: String, modifiedAt: Long, size: Long = 100) =
        CloudBackupFile(id = "id-$name", name = name, modifiedAt = modifiedAt, sizeBytes = size)

    @Test
    fun `starts disconnected`() {
        assertFalse(repository.isConnected())
        assertNull(repository.folderName())
    }

    @Test
    fun `connecting remembers the folder uri and its display name`() {
        val name = repository.connect("content://tree/1")

        assertEquals("Accounting-dev", name)
        assertTrue(repository.isConnected())
        assertEquals("Accounting-dev", repository.folderName())
        assertEquals("content://tree/1", store.folderUri)
    }

    @Test
    fun `a folder that cannot be read is not remembered`() {
        folder.failing = true

        try {
            repository.connect("content://tree/1")
            org.junit.Assert.fail("expected an IOException")
        } catch (expected: IOException) {
            // the screen reports it
        }

        assertFalse(repository.isConnected())
    }

    @Test
    fun `disconnecting forgets the folder`() {
        repository.connect("content://tree/1")

        repository.disconnect()

        assertFalse(repository.isConnected())
        assertNull(repository.folderName())
    }

    @Test
    fun `lists only accbak files, newest first`() {
        repository.connect("content://tree/1")
        setFiles(
            file("old.accbak", 100),
            file("notes.txt", 500),
            file("new.accbak", 300),
            file("mid.ACCBAK", 200)
        )

        assertEquals(listOf("new.accbak", "mid.ACCBAK", "old.accbak"), repository.listBackups().map { it.name })
    }

    @Test
    fun `files modified at the same time are ordered by name, latest name first`() {
        repository.connect("content://tree/1")
        setFiles(file("a_2026-01-01.accbak", 100), file("a_2026-01-02.accbak", 100))

        assertEquals(listOf("a_2026-01-02.accbak", "a_2026-01-01.accbak"), repository.listBackups().map { it.name })
    }

    @Test
    fun `lists the folder that was connected`() {
        repository.connect("content://tree/1")
        requestedUris.clear()

        repository.listBackups()

        assertEquals(listOf("content://tree/1"), requestedUris)
    }

    @Test(expected = IOException::class)
    fun `an unreachable folder fails the listing`() {
        repository.connect("content://tree/1")
        folder.failing = true

        repository.listBackups()
    }

    @Test(expected = IllegalStateException::class)
    fun `listing without a folder is a programming error`() {
        repository.listBackups()
    }

    @Test
    fun `uploads a file into the connected folder`() {
        repository.connect("content://tree/1")

        val created = repository.upload("a.accbak", byteArrayOf(1, 2, 3))

        assertEquals("a.accbak", created.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), repository.read(created))
        assertEquals(listOf("a.accbak"), folder.names())
    }

    @Test
    fun `pruning keeps the newest files of this prefix and leaves everything else alone`() {
        repository.connect("content://tree/1")
        (1..9).forEach { folder.add("mine_2026-01-0$it.accbak") }
        folder.add("other_2026-01-01.accbak")
        folder.add("mine_notes.txt")

        repository.prune("mine_", keep = 7)

        assertEquals(
            (3..9).map { "mine_2026-01-0$it.accbak" } + listOf("mine_notes.txt", "other_2026-01-01.accbak"),
            folder.names()
        )
    }

    @Test
    fun `pruning with fewer files than the limit deletes nothing`() {
        repository.connect("content://tree/1")
        (1..3).forEach { folder.add("mine_2026-01-0$it.accbak") }

        repository.prune("mine_", keep = 7)

        assertEquals(3, folder.files.size)
    }
}
