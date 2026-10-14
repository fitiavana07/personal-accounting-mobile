package dev.fitiavana.accounting.features.sync

/**
 * A channel that holds a lock for as long as it is open. Android drops incoming broadcast
 * packets to save power unless the Wi-Fi multicast lock is held, so discovery needs one; the
 * lock itself is passed in as [acquire] and [release] to keep this free of Android classes.
 */
class LockedDiscoveryChannel(
    private val delegate: DiscoveryChannel,
    acquire: () -> Unit,
    private val release: () -> Unit,
) : DiscoveryChannel by delegate {
    private var held = true

    init {
        acquire()
    }

    @Synchronized
    override fun close() {
        delegate.close()
        if (held) {
            held = false
            release()
        }
    }
}
