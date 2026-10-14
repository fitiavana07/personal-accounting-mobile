package dev.fitiavana.accounting.features.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** A datagram received from [host]. */
class Datagram(val bytes: ByteArray, val host: String)

/** How discovery packets travel: broadcast to everyone on the network, or sent to one host. */
interface DiscoveryChannel {
    val incoming: Flow<Datagram>

    suspend fun broadcast(bytes: ByteArray)

    suspend fun send(bytes: ByteArray, host: String)

    fun close()
}

/**
 * Finds the other devices of the group: announces this one regularly, answers queries, and keeps
 * [registry] up to date with who is around. [refresh] forgets everyone and asks who is there. Only
 * packets of the same build flavor ([identity]) count, so debug and release never see each other.
 */
class Discovery(
    private val scope: CoroutineScope,
    private val channel: DiscoveryChannel,
    private val registry: PeerRegistry,
    private val self: PeerInfo,
    private val port: Int,
    private val group: String,
    private val identity: SyncIdentity,
    private val announceIntervalMs: Long = 5_000,
    private val expireIntervalMs: Long = 1_000,
) {
    private var running: Job? = null

    fun start() {
        running = scope.launch {
            launch { channel.incoming.collect(::handle) }
            launch {
                while (true) {
                    channel.broadcast(packet(DiscoveryPacket.Type.ANNOUNCE))
                    delay(announceIntervalMs)
                }
            }
            launch {
                while (true) {
                    delay(expireIntervalMs)
                    registry.expire()
                }
            }
        }
    }

    /** Forgets every peer and asks the network to announce itself again. */
    fun refresh() {
        scope.launch {
            registry.clear()
            channel.broadcast(packet(DiscoveryPacket.Type.QUERY))
        }
    }

    fun stop() {
        running?.cancel()
        running = null
        channel.close()
        registry.clear()
    }

    private suspend fun handle(datagram: Datagram) {
        val received = DiscoveryPacket.decode(datagram.bytes, identity) ?: return
        registry.seen(received, datagram.host)
        val fromAnotherOfOurGroup = received.group == group && received.info.deviceId != self.deviceId
        if (received.type == DiscoveryPacket.Type.QUERY && fromAnotherOfOurGroup) {
            channel.send(packet(DiscoveryPacket.Type.ANNOUNCE).encode(identity), datagram.host)
        }
    }

    private fun packet(type: DiscoveryPacket.Type) = DiscoveryPacket(type, self, port, group)

    private suspend fun DiscoveryChannel.broadcast(packet: DiscoveryPacket) = broadcast(packet.encode(identity))
}
