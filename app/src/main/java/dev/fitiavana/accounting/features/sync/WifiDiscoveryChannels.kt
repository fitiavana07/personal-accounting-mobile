package dev.fitiavana.accounting.features.sync

import android.content.Context
import android.net.wifi.WifiManager
import java.io.IOException

/**
 * Opens the discovery channel of this build flavor: UDP on the flavor's port, with the Wi-Fi multicast
 * lock held for as long as the channel is open (without it Android drops incoming broadcasts to save
 * power). Binding the port can fail (no network, port taken): that is an [IOException], and the lock
 * is only taken once the port is bound.
 */
class WifiDiscoveryChannels(context: Context, internal val identity: SyncIdentity) {
    // The application context: a lock created from an activity would leak it.
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    /** Null on a device without Wi-Fi support. */
    internal val multicastLock: WifiManager.MulticastLock? =
        wifi?.createMulticastLock(LOCK_TAG)?.apply { setReferenceCounted(false) }

    @Throws(IOException::class)
    fun open(): DiscoveryChannel = LockedDiscoveryChannel(
        UdpDiscoveryChannel(identity.discoveryPort),
        acquire = { multicastLock?.acquire() },
        release = { multicastLock?.takeIf { it.isHeld }?.release() },
    )

    private companion object {
        const val LOCK_TAG = "accounting-sync"
    }
}
