package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.db.AppDatabase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The one place that versions local writes. Repositories call it inside their transaction
 * ([inTransaction]) for every record they create, change or delete, so each synced record always
 * has a [Meta] with the stamp of its latest write, and a deleted record leaves a tombstone.
 */
class ChangeTracker(
    private val dao: SyncMetaDao,
    private val clock: StampClock,
    private val runInTransaction: (Runnable) -> Unit,
) {
    private val announced = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var seeded = false

    /** Emits after every local write, so auto-sync can push it shortly after. */
    val changes: Flow<Unit> = announced

    /** Runs [block] in one database transaction: the write and its stamps land together or not at all. */
    fun <T> inTransaction(block: () -> T): T {
        var result: Any? = null
        runInTransaction(Runnable { result = block() })
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** Makes later stamps come after [time], the highest stamp seen on another device. */
    fun observe(time: Long) = clock.observe(Stamp(time, ""))

    fun touch(kind: SyncKind, id: String) = touch(kind, listOf(id))

    fun touch(kind: SyncKind, ids: Collection<String>) = write(ids.map { RecordKey(kind, it) }, deleted = false)

    /** Tombstones [keys]: for records already gone, or about to be, from their own table. */
    fun deleted(keys: Collection<RecordKey>) = write(keys, deleted = true)

    /**
     * A bulk replacement of all the data (a restore): [live] becomes the whole set of live records,
     * freshly stamped, and every other live record is tombstoned.
     */
    fun replaceAll(live: Set<RecordKey>) {
        deleted(dao.all().filter { !it.deleted && it.key() !in live }.map { it.key() })
        write(live, deleted = false)
    }

    /**
     * Tombstones an account and restamps the templates that pick it (the foreign key cascade removes
     * their entry). Must run before the account is deleted, because it reads what the cascade takes.
     */
    fun deletedAccount(id: String) {
        deleted(listOf(RecordKey(SyncKind.ACCOUNT, id)))
        touch(SyncKind.TEMPLATE, dao.templateIdsOfAccount(id))
    }

    @Synchronized
    private fun write(keys: Collection<RecordKey>, deleted: Boolean) {
        if (keys.isEmpty()) return
        if (!seeded) {
            clock.observe(Stamp(dao.highestTime(), ""))
            seeded = true
        }
        dao.upsert(keys.map { SyncMetaRow.of(it, Meta(clock.next(), deleted)) })
        announced.tryEmit(Unit)
    }

    companion object {
        fun forDatabase(
            database: AppDatabase,
            deviceId: String,
            now: () -> Long = System::currentTimeMillis,
        ) = ChangeTracker(database.syncMetaDao(), StampClock(deviceId, now), database::runInTransaction)
    }
}
