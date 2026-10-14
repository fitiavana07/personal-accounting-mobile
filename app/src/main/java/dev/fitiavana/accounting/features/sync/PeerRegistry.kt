package dev.fitiavana.accounting.features.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A device found on the network, ready to connect to. [label] is what to show: its name, made unique if needed. */
data class Peer(val info: PeerInfo, val host: String, val port: Int, val label: String)

/**
 * The devices currently around: those of our group that announced themselves within [ttlMillis].
 * [peers] only changes when the list really does, not on every heartbeat.
 */
class PeerRegistry(
    private val selfId: String,
    private val group: String,
    private val ttlMillis: Long = 15_000,
    private val now: () -> Long,
) {
    private class Entry(val info: PeerInfo, val host: String, val port: Int, val lastSeen: Long)

    private val entries = LinkedHashMap<String, Entry>()
    private val state = MutableStateFlow<List<Peer>>(emptyList())

    val peers: StateFlow<List<Peer>> = state

    /** Records a packet from [host]; returns whether it changed the list. Our own and foreign packets are ignored. */
    @Synchronized
    fun seen(packet: DiscoveryPacket, host: String): Boolean {
        if (packet.info.deviceId == selfId || packet.group != group) return false
        val previous = entries[packet.info.deviceId]
        entries[packet.info.deviceId] = Entry(packet.info, host, packet.port, now())
        val changed = previous == null || previous.host != host || previous.port != packet.port || previous.info != packet.info
        if (changed) publish()
        return changed
    }

    /** Drops the peers not heard from within the ttl; returns whether any was dropped. */
    @Synchronized
    fun expire(): Boolean {
        val removed = entries.values.removeAll { now() - it.lastSeen > ttlMillis }
        if (removed) publish()
        return removed
    }

    @Synchronized
    fun clear() {
        entries.clear()
        publish()
    }

    private fun publish() {
        val sameName = entries.values.groupingBy { it.info.name }.eachCount()
        state.value = entries.values
            .map { entry ->
                val label = if (sameName.getValue(entry.info.name) > 1) {
                    "${entry.info.name} · ${DeviceName.shortId(entry.info.deviceId)}"
                } else entry.info.name
                Peer(entry.info, entry.host, entry.port, label)
            }
            .sortedWith(compareBy({ it.label }, { it.info.deviceId }))
    }
}
