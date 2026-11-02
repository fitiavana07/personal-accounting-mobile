package dev.fitiavana.accounting.features.chainmonitors

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind
import java.util.UUID

/** Saves, lists and deletes chain monitors. Writes are synchronous: use them off the main thread. */
class ChainMonitorRepository(
    private val dao: ChainMonitorDao,
    private val tracker: ChangeTracker,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    fun getAll(): LiveData<List<ChainMonitor>> = dao.getAll()

    fun getAllSync(): List<ChainMonitor> = dao.getAllSync()

    fun get(id: String): ChainMonitor? = dao.getSync(id)

    /** Device-local detection state: never stamped, synced or backed up. */
    fun getStatesSync(): List<MonitorState> = dao.getAllStatesSync()

    fun saveStates(states: List<MonitorState>) = dao.upsertStates(states)

    fun create(
        label: String,
        holderAddress: String,
        tokenAddress: String,
        tokenSymbol: String,
        tokenDecimals: Int,
        threshold: String,
        intervalMinutes: Int
    ): ChainMonitor {
        val monitor = ChainMonitor(
            idGenerator(), label, holderAddress, tokenAddress, tokenSymbol, tokenDecimals,
            threshold, intervalMinutes, clock()
        )
        save(monitor)
        return monitor
    }

    fun update(monitor: ChainMonitor) = save(monitor)

    fun delete(id: String) = tracker.inTransaction {
        tracker.deleted(listOf(RecordKey(SyncKind.CHAIN_MONITOR, id)))
        dao.delete(id)
    }

    private fun save(monitor: ChainMonitor) = tracker.inTransaction {
        dao.insert(monitor)
        tracker.touch(SyncKind.CHAIN_MONITOR, monitor.id)
    }
}
