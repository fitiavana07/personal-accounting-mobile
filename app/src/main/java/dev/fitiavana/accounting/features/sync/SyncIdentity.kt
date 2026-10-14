package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.BuildConfig

/** Version of the sync protocol; read first in every message, another value is reported as incompatible. */
const val PROTOCOL_VERSION = 1

/** Who a device is on the network: its id and its friendly name. */
data class PeerInfo(val deviceId: String, val name: String)

/**
 * What separates one flavor of the app from another on the network. Debug and release builds can be
 * installed side by side, even on one phone, and must never see or sync with each other: each has its
 * own discovery port, its own app id in discovery packets, and its own crypto namespace (so even the
 * same passphrase gives a different key and group tag).
 */
data class SyncIdentity(
    val discoveryPort: Int,
    val appId: String,
    val cryptoNamespace: String,
) {
    companion object {
        val RELEASE = SyncIdentity(41889, "accounting-sync", "accounting-sync-v1")
        val DEBUG = SyncIdentity(41890, "accounting-sync-dev", "accounting-sync-dev-v1")

        fun forBuild(debug: Boolean = BuildConfig.DEBUG) = if (debug) DEBUG else RELEASE
    }
}
