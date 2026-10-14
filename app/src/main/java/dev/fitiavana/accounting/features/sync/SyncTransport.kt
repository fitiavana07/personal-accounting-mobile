package dev.fitiavana.accounting.features.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore

private const val CONNECT_TIMEOUT_MS = 3_000

/** A peer that goes quiet is given up on instead of blocking a session forever. */
private const val READ_TIMEOUT_MS = 15_000

/**
 * Listens for peers on a free TCP port and hands each connection to [handler]. It knows nothing
 * about syncing: the port is announced through discovery, and the handler runs the session. The
 * handler may block (it runs on an IO thread).
 */
class SyncServer(
    private val scope: CoroutineScope,
    private val handler: suspend (InputStream, OutputStream) -> Unit,
) {
    private var socket: ServerSocket? = null

    /** Anyone on the Wi-Fi can open a connection: only a few are served at once, the rest are dropped. */
    private val slots = Semaphore(MAX_CONNECTIONS)

    /** Starts listening and returns the port. */
    @Synchronized
    fun start(): Int {
        val server = ServerSocket(0)
        socket = server
        scope.launch(Dispatchers.IO) {
            while (true) {
                val client = try {
                    server.accept()
                } catch (e: IOException) {
                    return@launch // closed by stop()
                }
                launch { serve(client) }
            }
        }
        return server.localPort
    }

    @Synchronized
    fun stop() {
        socket?.close()
        socket = null
    }

    private suspend fun serve(client: Socket) {
        client.use {
            if (!slots.tryAcquire()) return
            try {
                it.soTimeout = READ_TIMEOUT_MS
                handler(it.getInputStream(), it.getOutputStream())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One bad connection must not stop the server; the handler reports its own failures.
            } finally {
                slots.release()
            }
        }
    }

    companion object {
        const val MAX_CONNECTIONS = 4
    }
}

object SyncClient {
    /** Connects to a peer and runs [block] on the connection; I/O failures surface as [IOException]. */
    suspend fun <T> connect(host: String, port: Int, block: suspend (InputStream, OutputStream) -> T): T =
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                block(socket.getInputStream(), socket.getOutputStream())
            }
        }
}
