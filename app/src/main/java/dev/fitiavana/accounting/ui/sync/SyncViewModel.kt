package dev.fitiavana.accounting.ui.sync

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.sync.PeerState
import dev.fitiavana.accounting.features.sync.SyncControls
import dev.fitiavana.accounting.features.sync.SyncSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the sync screen shows. */
data class SyncScreenState(
    val deviceName: String,
    val hasPassphrase: Boolean,
    /** The passphrase form: shown when there is no passphrase, or while the user is changing it. */
    val showPassphraseForm: Boolean,
    val passphraseTooShort: Boolean,
    val autoSync: Boolean,
    val peers: List<PeerState>,
    val lastSyncedAt: Long?,
    val networkError: String?,
)

/** The sync screen's logic: the passphrase form, the auto-sync switch, and what the running sync reports. */
class SyncViewModel(
    private val controls: SyncControls,
    private val settings: SyncSettingsStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : ViewModel() {

    private data class Form(val changing: Boolean = false, val tooShort: Boolean = false)

    private val form = MutableStateFlow(Form())
    private val mutableState = MutableLiveData<SyncScreenState>()

    val state: LiveData<SyncScreenState> = mutableState

    init {
        scope.launch {
            combine(controls.state, settings.passphrase, settings.autoSync, form) { sync, passphrase, autoSync, form ->
                val hasPassphrase = passphrase != null
                SyncScreenState(
                    deviceName = sync.deviceName,
                    hasPassphrase = hasPassphrase,
                    showPassphraseForm = !hasPassphrase || form.changing,
                    passphraseTooShort = form.tooShort,
                    autoSync = autoSync,
                    peers = sync.peers,
                    lastSyncedAt = sync.lastSyncedAt,
                    networkError = sync.networkError
                )
            }.collect { mutableState.value = it }
        }
    }

    /** Saves the passphrase if it has enough characters once trimmed; otherwise asks for a longer one. */
    fun savePassphrase(raw: String) {
        val passphrase = raw.trim()
        if (passphrase.length < MIN_PASSPHRASE_LENGTH) {
            form.update { it.copy(tooShort = true) }
            return
        }
        settings.setPassphrase(passphrase)
        form.value = Form()
    }

    /** The user is typing again: the "too short" message no longer applies. */
    fun passphraseEdited() {
        form.update { if (it.tooShort) it.copy(tooShort = false) else it }
    }

    fun startChangingPassphrase() = form.update { it.copy(changing = true) }

    fun cancelChangingPassphrase() {
        form.value = Form()
    }

    fun removePassphrase() {
        settings.setPassphrase(null)
        form.value = Form()
    }

    fun setAutoSync(enabled: Boolean) = settings.setAutoSync(enabled)

    fun refresh() = controls.refresh()

    fun syncAll() = controls.syncAll()

    fun syncWith(deviceId: String) = controls.syncWith(deviceId)

    override fun onCleared() = scope.cancel()

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}

class SyncViewModelFactory(
    private val controls: SyncControls,
    private val settings: SyncSettingsStore
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SyncViewModel(controls, settings) as T
    }
}
