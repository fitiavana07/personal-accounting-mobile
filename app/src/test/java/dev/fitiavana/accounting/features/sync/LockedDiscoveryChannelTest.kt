package dev.fitiavana.accounting.features.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LockedDiscoveryChannelTest {
    private class Recording : DiscoveryChannel {
        val events = mutableListOf<String>()
        override val incoming: Flow<Datagram> = emptyFlow()

        override suspend fun broadcast(bytes: ByteArray) {
            events += "broadcast ${String(bytes)}"
        }

        override suspend fun send(bytes: ByteArray, host: String) {
            events += "send ${String(bytes)} to $host"
        }

        override fun close() {
            events += "close"
        }
    }

    private val delegate = Recording()
    private val log = mutableListOf<String>()

    private fun locked() = LockedDiscoveryChannel(delegate, acquire = { log += "acquire" }, release = { log += "release" })

    @Test
    fun `the lock is held from the start to the close`() {
        val channel = locked()
        assertEquals(listOf("acquire"), log)

        channel.close()

        assertEquals(listOf("acquire", "release"), log)
        assertEquals(listOf("close"), delegate.events)
    }

    @Test
    fun `the lock is released only once when closed twice`() {
        val channel = locked()

        channel.close()
        channel.close()

        assertEquals(listOf("acquire", "release"), log)
    }

    @Test
    fun `everything else goes straight to the wrapped channel`() = runBlocking {
        val channel = locked()

        channel.broadcast("hi".toByteArray())
        channel.send("yo".toByteArray(), "1.2.3.4")

        assertEquals(listOf("broadcast hi", "send yo to 1.2.3.4"), delegate.events)
        assertSame(delegate.incoming, channel.incoming)
    }
}
