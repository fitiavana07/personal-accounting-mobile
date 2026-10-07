package dev.fitiavana.accounting.features.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SharedPreferencesBackupPrefsStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("test_backup", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun store() =
        SharedPreferencesBackupPrefsStore(context.getSharedPreferences("test_backup", Context.MODE_PRIVATE))

    @Test
    fun `auto-backup is enabled by default`() {
        assertTrue(store().isEnabled())
    }

    @Test
    fun `disabled state is remembered`() {
        store().setEnabled(false)

        assertFalse(store().isEnabled())
    }

    @Test
    fun `last backup time defaults to zero`() {
        assertEquals(0L, store().lastBackupAt())
    }

    @Test
    fun `last backup time is remembered`() {
        store().setLastBackupAt(1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, store().lastBackupAt())
    }
}
