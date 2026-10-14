package dev.fitiavana.accounting.features.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Where the last sync with one peer stands. */
sealed interface PeerSync {
    object Idle : PeerSync

    object Syncing : PeerSync

    data class Synced(val atMillis: Long, val changed: Boolean) : PeerSync

    data class Failed(val failure: SessionFailure, val atMillis: Long) : PeerSync
}

data class PeerState(val peer: Peer, val sync: PeerSync)

/** Everything the sync screen shows. */
data class SyncState(
    /** The app is in the foreground and sync is active (a passphrase is needed for it to find peers). */
    val running: Boolean = false,
    val hasPassphrase: Boolean = false,
    val autoSync: Boolean = true,
    val deviceName: String = "",
    val peers: List<PeerState> = emptyList(),
    val lastSyncedAt: Long? = null,
    /** Why sync could not open the network (no Wi-Fi, port taken...); refreshing tries again. */
    val networkError: String? = null,
)

/**
 * Runs sync while the app is in the foreground: discovers the peers of the group, serves their
 * sessions, and starts sessions of its own when it should:
 *  - a peer appears: only the device with the smaller id initiates, so two devices do not both sync;
 *  - the user changed something locally, shortly after it: pushed to every peer around (auto-sync);
 *  - the user asks, for one peer or for all.
 * Changing the passphrase restarts everything in the new group. Without one nothing is opened.
 */
class SyncCoordinator(
    private val scope: CoroutineScope,
    private val repository: SyncRepository,
    private val settings: SyncSettingsStore,
    private val changes: Flow<Unit>,
    private val identity: SyncIdentity,
    private val newChannel: () -> DiscoveryChannel,
    private val now: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = 3_000,
    private val announceIntervalMs: Long = 5_000,
) : SyncControls {
    /** The open network: the session logic for this passphrase, who is around, and discovery. */
    private class Active(val session: SyncSession, val registry: PeerRegistry, val discovery: Discovery)

    private val mutableState = MutableStateFlow(SyncState())
    private val statuses = MutableStateFlow<Map<String, PeerSync>>(emptyMap())
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** Bumped to open the network again after it failed to open. */
    private val retry = MutableStateFlow(0)

    /** Counts the local changes made; [syncedVersion] is what each peer has been synced up to. */
    private val localVersion = AtomicLong()
    private val syncedVersion = ConcurrentHashMap<String, Long>()

    @Volatile
    private var active: Active? = null

    @Volatile
    private var autoSync = true

    private var job: Job? = null

    override val state: StateFlow<SyncState> = mutableState

    /** Call when the app comes to the foreground. */
    @Synchronized
    fun start() {
        if (job != null) return
        mutableState.update { it.copy(running = true) }
        job = scope.launch {
            val deviceId = settings.deviceId()
            val self = PeerInfo(deviceId, DeviceName.of(deviceId))
            mutableState.update { it.copy(deviceName = self.name) }
            launch {
                settings.autoSync.collect { enabled ->
                    autoSync = enabled
                    mutableState.update { it.copy(autoSync = enabled) }
                }
            }
            launch { changes.collect { localVersion.incrementAndGet() } }
            launch { pushLocalChanges() }
            settings.passphrase.combine(retry) { passphrase, _ -> passphrase }.collectLatest { passphrase ->
                mutableState.update { it.copy(hasPassphrase = passphrase != null) }
                if (passphrase != null) runNetwork(self, passphrase)
            }
        }
    }

    /** Call when the app goes to the background. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        active = null
        statuses.value = emptyMap()
        mutableState.update { it.copy(running = false, peers = emptyList(), networkError = null) }
    }

    /**
     * Forgets the peers and asks the network who is there; the ones that answer are listed again.
     * If the network could not be opened, this tries to open it again.
     */
    override fun refresh() {
        val current = active
        if (current != null) current.discovery.refresh()
        else if (mutableState.value.hasPassphrase) retry.update { it + 1 }
    }

    override fun syncWith(deviceId: String) {
        scope.launch { sync(deviceId) }
    }

    override fun syncAll() {
        active?.registry?.peers?.value?.forEach { syncWith(it.info.deviceId) }
    }

    @OptIn(FlowPreview::class)
    private suspend fun pushLocalChanges() {
        changes.debounce(debounceMs).collect { if (autoSync) syncAll() }
    }

    /** Runs until cancelled: by a new passphrase or by [stop]. */
    private suspend fun runNetwork(self: PeerInfo, passphrase: String): Unit = coroutineScope {
        // The key derivation is deliberately slow: keep it off the caller's thread.
        val crypto = withContext(Dispatchers.Default) { SyncCrypto(passphrase, identity) }
        val registry = PeerRegistry(self.deviceId, crypto.groupTag, now = now)
        val session = SyncSession(repository, crypto, self)
        val server = SyncServer(this) { input, output ->
            val version = localVersion.get()
            (session.respond(input, output) as? SessionResult.Synced)?.let { record(it.peer.deviceId, it, version) }
        }
        var channel: DiscoveryChannel? = null
        val discovery = try {
            channel = newChannel()
            Discovery(this, channel, registry, self, server.start(), crypto.groupTag, identity, announceIntervalMs)
        } catch (e: IOException) {
            // No Wi-Fi, a port already taken...: say so and wait for a retry or another passphrase.
            channel?.close()
            server.stop()
            mutableState.update { it.copy(networkError = e.message ?: "The network could not be opened") }
            awaitCancellation()
        }
        mutableState.update { it.copy(networkError = null) }
        active = Active(session, registry, discovery)
        discovery.start()
        try {
            var known = emptySet<String>()
            registry.peers.collect { peers ->
                val appeared = peers.filter { it.info.deviceId !in known }
                known = peers.map { it.info.deviceId }.toSet()
                publishPeers()
                if (autoSync) {
                    // Of two devices, the smaller id starts the session; the other one only does when it has
                    // something the peer has not seen, e.g. a change made before the peer was found.
                    appeared
                        .filter { self.deviceId < it.info.deviceId || hasUnsyncedChanges(it.info.deviceId) }
                        .forEach { launch { sync(it.info.deviceId) } }
                }
            }
        } finally {
            active = null
            discovery.stop()
            server.stop()
            publishPeers()
        }
    }

    private suspend fun sync(deviceId: String) {
        val current = active ?: return
        val peer = current.registry.peers.value.firstOrNull { it.info.deviceId == deviceId } ?: return
        if (!inFlight.add(deviceId)) return
        try {
            setStatus(deviceId, PeerSync.Syncing)
            val version = localVersion.get()
            val result = try {
                SyncClient.connect(peer.host, peer.port) { input, output -> current.session.initiate(input, output) }
            } catch (e: IOException) {
                SessionResult.Failed(SessionFailure.Network(e.message ?: "The connection failed"))
            }
            record(deviceId, result, version)
        } finally {
            inFlight.remove(deviceId)
        }
    }

    /** [version] is the local version when the session started: later changes are not in it. */
    private fun record(deviceId: String, result: SessionResult, version: Long) {
        when (result) {
            is SessionResult.Synced -> {
                syncedVersion.merge(deviceId, version, ::maxOf)
                mutableState.update { it.copy(lastSyncedAt = now()) }
                setStatus(deviceId, PeerSync.Synced(now(), result.changed))
            }
            is SessionResult.Failed -> setStatus(deviceId, PeerSync.Failed(result.failure, now()))
        }
    }

    /** Local changes this peer has not been synced with: it must hear about them when it shows up. */
    private fun hasUnsyncedChanges(deviceId: String) = localVersion.get() > (syncedVersion[deviceId] ?: 0L)

    private fun setStatus(deviceId: String, status: PeerSync) {
        statuses.update { it + (deviceId to status) }
        publishPeers()
    }

    private fun publishPeers() {
        val peers = active?.registry?.peers?.value.orEmpty()
        mutableState.update { state ->
            state.copy(peers = peers.map { PeerState(it, statuses.value[it.info.deviceId] ?: PeerSync.Idle) })
        }
    }
}
