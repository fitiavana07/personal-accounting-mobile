package dev.fitiavana.accounting.ui.sync

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.features.sync.Peer
import dev.fitiavana.accounting.features.sync.PeerInfo
import dev.fitiavana.accounting.features.sync.PeerState
import dev.fitiavana.accounting.features.sync.PeerSync
import dev.fitiavana.accounting.features.sync.SyncControls
import dev.fitiavana.accounting.features.sync.SyncSettingsStore
import dev.fitiavana.accounting.features.sync.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private class FakeControls : SyncControls {
        val mutable = MutableStateFlow(SyncState(running = true, deviceName = "Brave Otter"))
        val calls = mutableListOf<String>()
        override val state: StateFlow<SyncState> = mutable
        override fun refresh() { calls += "refresh" }
        override fun syncWith(deviceId: String) { calls += "syncWith $deviceId" }
        override fun syncAll() { calls += "syncAll" }
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val controls = FakeControls()
    private lateinit var settings: SyncSettingsStore
    private lateinit var viewModel: SyncViewModel

    @Before
    fun setUp() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("sync-vm-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = SyncSettingsStore(prefs, newId = { "dev" })
        viewModel = SyncViewModel(controls, settings, scope)
    }

    @After
    fun tearDown() = scope.cancel()

    private val state get() = viewModel.state.value!!

    @Test
    fun `the screen shows what the running sync reports`() {
        val peer = PeerState(Peer(PeerInfo("p", "Agile Ant"), "h", 1, "Agile Ant"), PeerSync.Idle)
        controls.mutable.value = controls.mutable.value.copy(peers = listOf(peer), lastSyncedAt = 99L, networkError = "no wifi")

        assertEquals("Brave Otter", state.deviceName)
        assertEquals(listOf(peer), state.peers)
        assertEquals(99L, state.lastSyncedAt)
        assertEquals("no wifi", state.networkError)
    }

    @Test
    fun `without a passphrase the form is shown`() {
        assertFalse(state.hasPassphrase)
        assertTrue(state.showPassphraseForm)
    }

    @Test
    fun `a passphrase shorter than 8 characters is refused with a message and nothing is saved`() {
        viewModel.savePassphrase("short")

        assertTrue(state.passphraseTooShort)
        assertTrue(state.showPassphraseForm)
        assertNull(settings.passphrase.value)
    }

    @Test
    fun `the length counts after trimming`() {
        viewModel.savePassphrase("   1234567  ")

        assertTrue(state.passphraseTooShort)
        assertNull(settings.passphrase.value)
    }

    @Test
    fun `a good passphrase is saved trimmed and hides the form`() {
        viewModel.savePassphrase("  correct horse  ")

        assertEquals("correct horse", settings.passphrase.value)
        assertTrue(state.hasPassphrase)
        assertFalse(state.showPassphraseForm)
        assertFalse(state.passphraseTooShort)
    }

    @Test
    fun `the message goes away once the user types again`() {
        viewModel.savePassphrase("short")

        viewModel.passphraseEdited()

        assertFalse(state.passphraseTooShort)
    }

    @Test
    fun `changing the passphrase shows the form again until saved or cancelled`() {
        viewModel.savePassphrase("correct horse")

        viewModel.startChangingPassphrase()
        assertTrue(state.showPassphraseForm)

        viewModel.cancelChangingPassphrase()
        assertFalse(state.showPassphraseForm)

        viewModel.startChangingPassphrase()
        viewModel.savePassphrase("another passphrase")
        assertEquals("another passphrase", settings.passphrase.value)
        assertFalse(state.showPassphraseForm)
    }

    @Test
    fun `a too short new passphrase keeps the old one`() {
        viewModel.savePassphrase("correct horse")
        viewModel.startChangingPassphrase()

        viewModel.savePassphrase("short")

        assertEquals("correct horse", settings.passphrase.value)
        assertTrue(state.showPassphraseForm)
        assertTrue(state.passphraseTooShort)
    }

    @Test
    fun `removing the passphrase brings the form back`() {
        viewModel.savePassphrase("correct horse")

        viewModel.removePassphrase()

        assertNull(settings.passphrase.value)
        assertFalse(state.hasPassphrase)
        assertTrue(state.showPassphraseForm)
    }

    @Test
    fun `auto-sync follows the switch`() {
        assertTrue(state.autoSync)

        viewModel.setAutoSync(false)

        assertFalse(state.autoSync)
        assertFalse(settings.autoSync.value)
    }

    @Test
    fun `the buttons are passed on to the running sync`() {
        viewModel.refresh()
        viewModel.syncAll()
        viewModel.syncWith("p")

        assertEquals(listOf("refresh", "syncAll", "syncWith p"), controls.calls)
    }
}
