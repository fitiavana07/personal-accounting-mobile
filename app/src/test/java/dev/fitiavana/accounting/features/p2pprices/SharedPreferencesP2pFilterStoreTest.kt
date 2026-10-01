package dev.fitiavana.accounting.features.p2pprices

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.network.p2p.P2pPaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SharedPreferencesP2pFilterStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("test_p2p", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun store() =
        SharedPreferencesP2pFilterStore(context.getSharedPreferences("test_p2p", Context.MODE_PRIVATE))

    @Test
    fun `nothing saved gives null selection and null methods`() {
        assertNull(store().loadSelected())
        assertNull(store().loadMethods())
    }

    @Test
    fun `selection survives a new store instance`() {
        store().saveSelected("Mvola")

        assertEquals("Mvola", store().loadSelected())
    }

    @Test
    fun `saving null clears the selection`() {
        store().saveSelected("Mvola")
        store().saveSelected(null)

        assertNull(store().loadSelected())
    }

    @Test
    fun `methods round trip in order`() {
        val methods = listOf(P2pPaymentMethod("Mvola", "Mvola"), P2pPaymentMethod("OrangeMoney", "Orange Money - OM"))

        store().saveMethods(methods)

        assertEquals(methods, store().loadMethods())
    }
}
