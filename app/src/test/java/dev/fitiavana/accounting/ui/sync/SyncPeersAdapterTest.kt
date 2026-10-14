package dev.fitiavana.accounting.ui.sync

import android.content.Context
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.sync.Peer
import dev.fitiavana.accounting.features.sync.PeerInfo
import dev.fitiavana.accounting.features.sync.PeerState
import dev.fitiavana.accounting.features.sync.PeerSync
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncPeersAdapterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>().apply { setTheme(R.style.Theme_Accounting) }
    private val tapped = mutableListOf<String>()
    private val adapter = SyncPeersAdapter { tapped += it }

    private fun peer(id: String, label: String, sync: PeerSync = PeerSync.Idle) =
        PeerState(Peer(PeerInfo(id, label), "h", 1, label), sync)

    private fun bind(position: Int): android.view.View {
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, position)
        return holder.itemView
    }

    @Test
    fun `each peer is one row with its label and status`() {
        adapter.submit(listOf(peer("a", "Agile Ant"), peer("b", "Brave Bee", PeerSync.Syncing)))

        assertEquals(2, adapter.itemCount)
        assertEquals("Agile Ant", bind(0).findViewById<TextView>(R.id.text_peer_name).text.toString())
        assertEquals("Tap to sync", bind(0).findViewById<TextView>(R.id.text_peer_status).text.toString())
        assertEquals("Syncing…", bind(1).findViewById<TextView>(R.id.text_peer_status).text.toString())
    }

    @Test
    fun `tapping a row syncs with that peer`() {
        adapter.submit(listOf(peer("a", "Agile Ant"), peer("b", "Brave Bee")))

        bind(1).performClick()

        assertEquals(listOf("b"), tapped)
    }
}
