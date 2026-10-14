package dev.fitiavana.accounting.ui.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.features.sync.PeerSync
import dev.fitiavana.accounting.features.sync.SessionFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncStatusFormatterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val at = 1_790_000_000_000L
    private val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))

    private fun text(status: PeerSync) = SyncStatusFormatter.peer(context, status)

    @Test
    fun `an idle peer invites a tap`() {
        assertEquals("Tap to sync", text(PeerSync.Idle))
    }

    @Test
    fun `a peer being synced says so`() {
        assertEquals("Syncing…", text(PeerSync.Syncing))
    }

    @Test
    fun `a synced peer gives the time and whether data changed`() {
        assertEquals("Synced at $time", text(PeerSync.Synced(at, changed = false)))
        assertEquals("Synced at $time · data updated", text(PeerSync.Synced(at, changed = true)))
    }

    @Test
    fun `each failure is explained in plain terms`() {
        assertTrue(text(PeerSync.Failed(SessionFailure.NoAnswer, at)).contains("passphrase"))
        assertTrue(text(PeerSync.Failed(SessionFailure.WrongPassphrase, at)).contains("passphrase"))
        assertTrue(text(PeerSync.Failed(SessionFailure.Incompatible("protocol 2"), at)).contains("protocol 2"))
        assertTrue(text(PeerSync.Failed(SessionFailure.Refused("database version"), at)).contains("database version"))
        assertTrue(text(PeerSync.Failed(SessionFailure.Invalid("bad json"), at)).contains("bad json"))
        assertTrue(text(PeerSync.Failed(SessionFailure.Network("timed out"), at)).contains("timed out"))
    }

    @Test
    fun `the last sync time reads never when there is none`() {
        assertEquals("Last synced: never", SyncStatusFormatter.lastSynced(context, null))
        assertEquals("Last synced: $time", SyncStatusFormatter.lastSynced(context, at))
    }
}
