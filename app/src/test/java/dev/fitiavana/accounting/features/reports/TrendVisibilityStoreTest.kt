package dev.fitiavana.accounting.features.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class TrendVisibilityStoreTest {

    private fun prefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(SharedPreferencesTrendVisibilityStore.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun `trend charts are visible by default`() {
        assertTrue(SharedPreferencesTrendVisibilityStore(prefs()).isVisible())
    }

    @Test
    fun `the choice is persisted and read back by a new store`() {
        SharedPreferencesTrendVisibilityStore(prefs()).setVisible(false)

        assertFalse(SharedPreferencesTrendVisibilityStore(prefs()).isVisible())

        SharedPreferencesTrendVisibilityStore(prefs()).setVisible(true)

        assertTrue(SharedPreferencesTrendVisibilityStore(prefs()).isVisible())
    }

    @Test
    fun `in-memory store defaults to visible and remembers the choice`() {
        val store = InMemoryTrendVisibilityStore()
        assertTrue(store.isVisible())

        store.setVisible(false)

        assertFalse(store.isVisible())
    }
}
