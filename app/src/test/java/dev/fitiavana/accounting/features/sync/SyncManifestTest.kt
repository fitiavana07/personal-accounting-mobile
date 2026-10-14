package dev.fitiavana.accounting.features.sync

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sync needs the network, the Wi-Fi state, and the multicast lock without which Android drops broadcasts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncManifestTest {
    private val requested: Set<String> = ApplicationProvider.getApplicationContext<Context>().let { context ->
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
    }

    @Test
    fun `the app asks for the permissions P2P sync needs`() {
        assertTrue(requested.contains("android.permission.INTERNET"))
        assertTrue(requested.contains("android.permission.ACCESS_WIFI_STATE"))
        assertTrue(requested.contains("android.permission.CHANGE_WIFI_MULTICAST_STATE"))
    }
}
