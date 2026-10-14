package dev.fitiavana.accounting.features.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncSettingsStoreTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("sync-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun store(newId: () -> String = { "generated" }) = SyncSettingsStore(prefs, newId)

    @Test
    fun `the device id is generated on first use and then stays the same`() {
        var counter = 0
        val store = store { "dev-${++counter}" }

        assertEquals("dev-1", store.deviceId())
        assertEquals("dev-1", store.deviceId())
    }

    @Test
    fun `the device id survives a new store over the same preferences`() {
        val first = store { "first" }.deviceId()

        assertEquals(first, store { "second" }.deviceId())
    }

    @Test
    fun `there is no passphrase initially`() {
        assertNull(store().passphrase.value)
    }

    @Test
    fun `a passphrase is stored and can be cleared`() {
        val store = store()

        store.setPassphrase("correct horse")
        assertEquals("correct horse", store.passphrase.value)
        assertEquals("correct horse", store().passphrase.value)

        store.setPassphrase(null)
        assertNull(store.passphrase.value)
        assertNull(store().passphrase.value)
    }

    @Test
    fun `a blank passphrase counts as none`() {
        val store = store()
        store.setPassphrase("secret")

        store.setPassphrase("   ")

        assertNull(store.passphrase.value)
    }

    @Test
    fun `the passphrase is trimmed`() {
        val store = store()

        store.setPassphrase("  secret phrase  ")

        assertEquals("secret phrase", store.passphrase.value)
    }

    @Test
    fun `auto-sync is on by default and can be switched off`() {
        val store = store()
        assertTrue(store.autoSync.value)

        store.setAutoSync(false)

        assertEquals(false, store.autoSync.value)
        assertEquals(false, store().autoSync.value)
    }
}
