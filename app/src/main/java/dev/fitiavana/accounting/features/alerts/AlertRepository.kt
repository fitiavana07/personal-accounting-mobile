package dev.fitiavana.accounting.features.alerts

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind
import java.util.UUID

/** Saves, lists and deletes gain/loss alerts. Writes are synchronous: use them off the main thread. */
class AlertRepository(
    private val dao: AlertDao,
    private val tracker: ChangeTracker,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    fun getAll(): LiveData<List<GainLossAlert>> = dao.getAll()

    fun getAllSync(): List<GainLossAlert> = dao.getAllSync()

    fun get(id: String): GainLossAlert? = dao.getSync(id)

    /** Device-local detection state: never stamped, synced or backed up. */
    fun getStatesSync(): List<AlertState> = dao.getAllStatesSync()

    fun saveStates(states: List<AlertState>) = dao.upsertStates(states)

    fun create(accountId: String, type: String, thresholdPercent: Double): GainLossAlert {
        val alert = GainLossAlert(idGenerator(), accountId, type, thresholdPercent, clock())
        save(alert)
        return alert
    }

    fun update(alert: GainLossAlert) = save(alert)

    fun delete(id: String) = tracker.inTransaction {
        tracker.deleted(listOf(RecordKey(SyncKind.ALERT, id)))
        dao.delete(id)
    }

    private fun save(alert: GainLossAlert) = tracker.inTransaction {
        dao.insert(alert)
        tracker.touch(SyncKind.ALERT, alert.id)
    }
}
