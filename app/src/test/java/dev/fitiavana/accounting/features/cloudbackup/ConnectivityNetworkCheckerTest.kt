package dev.fitiavana.accounting.features.cloudbackup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class ConnectivityNetworkCheckerTest {

    private lateinit var context: Context
    private lateinit var connectivity: ConnectivityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Test
    fun `online when the active network reaches the internet`() {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, capabilities)

        assertTrue(ConnectivityNetworkChecker(context).isOnline())
    }

    @Test
    fun `offline when the active network does not reach the internet`() {
        shadowOf(connectivity).setNetworkCapabilities(connectivity.activeNetwork, ShadowNetworkCapabilities.newInstance())

        assertFalse(ConnectivityNetworkChecker(context).isOnline())
    }

    @Test
    fun `offline when there is no active network`() {
        shadowOf(connectivity).setDefaultNetworkActive(false)
        shadowOf(connectivity).setActiveNetworkInfo(null)

        assertFalse(ConnectivityNetworkChecker(context).isOnline())
    }
}
