package dev.fitiavana.accounting.features.cexprices

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SharedPreferencesCexPairStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("test_cex", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun store() =
        SharedPreferencesCexPairStore(context.getSharedPreferences("test_cex", Context.MODE_PRIVATE))

    @Test
    fun `load returns null when nothing saved`() {
        assertNull(store().load())
    }

    @Test
    fun `load returns the saved pair`() {
        store().save("BTC", "USDT")

        assertEquals("BTC" to "USDT", store().load())
    }

    @Test
    fun `save overwrites the previous pair`() {
        store().save("BTC", "USDT")
        store().save("ETH", "USDC")

        assertEquals("ETH" to "USDC", store().load())
    }
}
