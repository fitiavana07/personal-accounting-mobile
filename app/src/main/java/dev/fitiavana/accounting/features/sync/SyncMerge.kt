package dev.fitiavana.accounting.features.sync

/**
 * The kinds of synced record. A transaction is one record together with its entries, and a template
 * one record together with its entries, so their versions can never be mixed.
 */
enum class SyncKind { INSTRUMENT, ACCOUNT, TRANSACTION, TEMPLATE, SETTINGS }

/** [id] is the row id, the instrument code for instruments, and a fixed id for the singleton settings. */
data class RecordKey(val kind: SyncKind, val id: String)

/** Version information kept for every synced row; a deleted row stays as a tombstone. */
data class Meta(val stamp: Stamp, val deleted: Boolean)

data class MergeResult(
    val meta: Map<RecordKey, Meta>,
    /** Records whose remote version won, so the caller must write them locally. */
    val takenFromRemote: Set<RecordKey>,
)

object SyncMerge {
    /** Last write wins per record: the higher stamp wins, a tombstone competes like any edit. */
    fun merge(local: Map<RecordKey, Meta>, remote: Map<RecordKey, Meta>): MergeResult {
        val taken = remote.filter { (key, meta) ->
            val mine = local[key]
            mine == null || meta.stamp > mine.stamp
        }
        return MergeResult(meta = local + taken, takenFromRemote = taken.keys)
    }

    fun highestTime(meta: Map<RecordKey, Meta>): Long = meta.values.maxOfOrNull { it.stamp.time } ?: 0
}
