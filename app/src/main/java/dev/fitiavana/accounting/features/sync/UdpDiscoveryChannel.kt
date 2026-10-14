package dev.fitiavana.accounting.features.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * Discovery over UDP: packets are broadcast on every local network and received on [port] (from
 * [SyncIdentity.discoveryPort]: debug and release builds use different ports). A peer's unicast
 * replies are sent to [peerPort], which is the same port on real devices.
 */
class UdpDiscoveryChannel(
    port: Int,
    private val peerPort: Int = port,
) : DiscoveryChannel {
    private val socket = DatagramSocket(null).apply {
        reuseAddress = true
        broadcast = true
        bind(InetSocketAddress(port))
    }

    override val incoming: Flow<Datagram> = channelFlow {
        launch(Dispatchers.IO) {
            val buffer = ByteArray(RECEIVE_BUFFER)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: IOException) {
                    break // the socket was closed
                }
                trySend(Datagram(packet.data.copyOf(packet.length), packet.address.hostAddress.orEmpty()))
            }
            close()
        }
        awaitClose { socket.close() }
    }

    override suspend fun broadcast(bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            broadcastAddresses().forEach { address -> transmit(bytes, address) }
        }
    }

    override suspend fun send(bytes: ByteArray, host: String) {
        withContext(Dispatchers.IO) {
            try {
                transmit(bytes, InetAddress.getByName(host))
            } catch (e: IOException) {
                // A peer that cannot be reached is simply not discovered.
            }
        }
    }

    override fun close() = socket.close()

    /** Delivery is best effort: a failure on one network must not stop the others. */
    private fun transmit(bytes: ByteArray, address: InetAddress) {
        try {
            socket.send(DatagramPacket(bytes, bytes.size, address, peerPort))
        } catch (e: IOException) {
            // Closed, or no route on this interface.
        }
    }

    /** The broadcast address of every IPv4 network this device is on, plus the generic one. */
    private fun broadcastAddresses(): List<InetAddress> {
        val perInterface = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
        } catch (e: IOException) {
            emptyList()
        }
        return (perInterface + InetAddress.getByName("255.255.255.255")).distinct()
    }

    private companion object {
        const val RECEIVE_BUFFER = 1500
    }
}
