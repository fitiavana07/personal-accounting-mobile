package dev.fitiavana.accounting.features.sync

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert

/**
 * Version of one synced record, kept beside the data so the feature tables stay as they are. A
 * deleted record is gone from its own table and only lives on here as a tombstone.
 */
@Entity(tableName = "sync_meta", primaryKeys = ["kind", "id"])
data class SyncMetaRow(
    val kind: SyncKind,
    val id: String,
    val stampTime: Long,
    val stampDevice: String,
    val deleted: Boolean,
) {
    fun key() = RecordKey(kind, id)

    fun meta() = Meta(Stamp(stampTime, stampDevice), deleted)

    companion object {
        fun of(key: RecordKey, meta: Meta) =
            SyncMetaRow(key.kind, key.id, meta.stamp.time, meta.stamp.deviceId, meta.deleted)
    }
}

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta")
    fun all(): List<SyncMetaRow>

    @Upsert
    fun upsert(rows: List<SyncMetaRow>)

    @Query("DELETE FROM sync_meta")
    fun deleteAll()

    @Query("SELECT COALESCE(MAX(stampTime), 0) FROM sync_meta")
    fun highestTime(): Long

    /** The templates that pick [accountId]: deleting the account changes them, through the foreign key cascade. */
    @Query("SELECT DISTINCT templateId FROM template_entries WHERE accountId = :accountId")
    fun templateIdsOfAccount(accountId: String): List<String>
}
