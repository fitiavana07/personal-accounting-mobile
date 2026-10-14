package dev.fitiavana.accounting.features.sync

import kotlinx.coroutines.flow.StateFlow

/** What the sync screen can see and ask of the running sync. */
interface SyncControls {
    val state: StateFlow<SyncState>

    /** Forgets the peers and asks the network who is there. */
    fun refresh()

    fun syncWith(deviceId: String)

    fun syncAll()
}
