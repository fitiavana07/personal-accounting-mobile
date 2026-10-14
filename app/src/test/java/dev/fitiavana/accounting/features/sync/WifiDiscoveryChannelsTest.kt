package dev.fitiavana.accounting.features.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.DatagramSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class WifiDiscoveryChannelsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opened = mutableListOf<DiscoveryChannel>()

    @After
    fun tearDown() = opened.forEach { it.close() }

    private fun freePort() = DatagramSocket(0).use { it.localPort }

    private fun channels(port: Int = freePort()) =
        WifiDiscoveryChannels(context, SyncIdentity.RELEASE.copy(discoveryPort = port))

    @Test
    fun `the multicast lock is held while the channel is open and released when it closes`() {
        val channels = channels()

        val channel = channels.open().also(opened::add)
        assertTrue(channels.multicastLock?.isHeld == true)

        channel.close()
        assertFalse(channels.multicastLock?.isHeld == true)
    }

    @Test
    fun `the channel listens on the port of the build's identity`() {
        val port = freePort()
        val channels = channels(port)
        channels.open().also(opened::add)

        // Another device of the same flavor on this port: binding it again must work (address reuse), not fail.
        channels.open().also(opened::add)

        assertEquals(port, channels.identity.discoveryPort)
    }

    @Test
    fun `a port that cannot be bound is reported as an IOException and takes no lock`() {
        val busy = DatagramSocket(0) // bound without address reuse: nobody else can take the port
        val channels = channels(busy.localPort)

        try {
            assertThrows(IOException::class.java) { channels.open() }
            assertFalse(channels.multicastLock?.isHeld == true)
        } finally {
            busy.close()
        }
    }
}
