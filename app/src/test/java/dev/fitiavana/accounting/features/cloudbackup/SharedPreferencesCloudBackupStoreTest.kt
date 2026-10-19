package dev.fitiavana.accounting.features.cloudbackup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SharedPreferencesCloudBackupStoreTest {

    /** Reverses the text: reversible, and visibly not the plain passphrase. */
    private class FakeSecretCipher : SecretCipher {
        var lost = false
        override fun seal(plain: String): String = plain.reversed()
        override fun open(sealed: String): String? = if (lost) null else sealed.reversed()
    }

    private lateinit var context: Context
    private val cipher = FakeSecretCipher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
    }

    private fun prefs() = context.getSharedPreferences("test_cloud_backup", Context.MODE_PRIVATE)

    private fun store() = SharedPreferencesCloudBackupStore(prefs(), cipher)

    @Test
    fun `nothing is connected or configured by default`() {
        val store = store()

        assertNull(store.folderUri)
        assertNull(store.folderName)
        assertFalse(store.enabled)
        assertNull(store.passphrase)
        assertEquals(0L, store.lastSuccessAt)
        assertNull(store.lastError)
        assertEquals(0, store.consecutiveFailures)
        assertFalse(store.failureNotified)
    }

    @Test
    fun `the folder is remembered`() {
        store().setFolder("content://tree/1", "Accounting")

        assertEquals("content://tree/1", store().folderUri)
        assertEquals("Accounting", store().folderName)
    }

    @Test
    fun `clearing forgets the folder`() {
        store().setFolder("content://tree/1", "Accounting")

        store().clearFolder()

        assertNull(store().folderUri)
        assertNull(store().folderName)
    }

    @Test
    fun `the status is remembered`() {
        store().apply {
            enabled = true
            enabledAt = 5L
            lastSuccessAt = 10L
            lastError = CloudBackupError.OFFLINE
            consecutiveFailures = 2
            failureNotified = true
        }

        val store = store()
        assertTrue(store.enabled)
        assertEquals(5L, store.enabledAt)
        assertEquals(10L, store.lastSuccessAt)
        assertEquals(CloudBackupError.OFFLINE, store.lastError)
        assertEquals(2, store.consecutiveFailures)
        assertTrue(store.failureNotified)
    }

    @Test
    fun `an error can be cleared`() {
        store().lastError = CloudBackupError.FOLDER

        store().lastError = null

        assertNull(store().lastError)
    }

    @Test
    fun `an unknown stored error is ignored`() {
        prefs().edit().putString("last_error", "FROM_THE_FUTURE").commit()

        assertNull(store().lastError)
    }

    @Test
    fun `the passphrase is stored sealed, never in the clear`() {
        store().passphrase = "correct horse"

        assertEquals("correct horse", store().passphrase)
        assertFalse(prefs().all.values.any { it.toString().contains("correct horse") })
    }

    @Test
    fun `a passphrase that can no longer be opened reads as missing`() {
        store().passphrase = "correct horse"
        cipher.lost = true

        assertNull(store().passphrase)
    }

    @Test
    fun `removing the passphrase forgets it`() {
        store().passphrase = "correct horse"

        store().passphrase = null

        assertNull(store().passphrase)
        assertFalse(prefs().contains("passphrase"))
    }
}
