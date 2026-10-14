package dev.fitiavana.accounting.features.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private val key = RecordKey(SyncKind.TRANSACTION, "t1")
    private val other = RecordKey(SyncKind.ACCOUNT, "a1")

    private fun meta(time: Long, device: String = "a", deleted: Boolean = false) =
        Meta(Stamp(time, device), deleted)

    private fun merged(local: Map<RecordKey, Meta>, remote: Map<RecordKey, Meta>) =
        SyncMerge.merge(local, remote)

    @Test
    fun aRecordOnlyOnTheRemoteIsTaken() {
        val result = merged(emptyMap(), mapOf(key to meta(1)))

        assertEquals(mapOf(key to meta(1)), result.meta)
        assertEquals(setOf(key), result.takenFromRemote)
    }

    @Test
    fun aRecordOnlyLocalIsKept() {
        val result = merged(mapOf(key to meta(1)), emptyMap())

        assertEquals(mapOf(key to meta(1)), result.meta)
        assertTrue(result.takenFromRemote.isEmpty())
    }

    @Test
    fun theHigherStampWins() {
        val newerRemote = merged(mapOf(key to meta(1)), mapOf(key to meta(2, "b")))
        val newerLocal = merged(mapOf(key to meta(3)), mapOf(key to meta(2, "b")))

        assertEquals(meta(2, "b"), newerRemote.meta.getValue(key))
        assertEquals(setOf(key), newerRemote.takenFromRemote)
        assertEquals(meta(3), newerLocal.meta.getValue(key))
        assertTrue(newerLocal.takenFromRemote.isEmpty())
    }

    @Test
    fun equalTimesAreBrokenByTheDeviceId() {
        val result = merged(mapOf(key to meta(5, "a")), mapOf(key to meta(5, "b")))

        assertEquals(meta(5, "b"), result.meta.getValue(key))
    }

    @Test
    fun aNewerTombstoneBeatsAnOlderEdit() {
        val result = merged(mapOf(key to meta(1)), mapOf(key to meta(2, "b", deleted = true)))

        assertTrue(result.meta.getValue(key).deleted)
        assertEquals(setOf(key), result.takenFromRemote)
    }

    @Test
    fun aNewerEditBeatsAnOlderTombstone() {
        val result = merged(mapOf(key to meta(1, deleted = true)), mapOf(key to meta(2, "b")))

        assertEquals(meta(2, "b"), result.meta.getValue(key))
    }

    @Test
    fun theSameStampIsNotTakenAgain() {
        val result = merged(mapOf(key to meta(4)), mapOf(key to meta(4)))

        assertTrue(result.takenFromRemote.isEmpty())
    }

    @Test
    fun mergingIsCommutativeAssociativeAndIdempotent() {
        val x = mapOf(key to meta(1, "a"), other to meta(7, "a", deleted = true))
        val y = mapOf(key to meta(3, "b"), other to meta(5, "b"))
        val z = mapOf(key to meta(3, "c"), RecordKey(SyncKind.INSTRUMENT, "USD") to meta(2, "c"))

        fun m(a: Map<RecordKey, Meta>, b: Map<RecordKey, Meta>) = merged(a, b).meta

        assertEquals(m(x, y), m(y, x))
        assertEquals(m(m(x, y), z), m(x, m(y, z)))
        assertEquals(x, m(x, x))
        assertEquals(m(x, y), m(m(x, y), y))
    }

    @Test
    fun highestTimeCoversEveryRecord() {
        val result = merged(mapOf(key to meta(3)), mapOf(other to meta(9, "b")))

        assertEquals(9L, SyncMerge.highestTime(result.meta))
        assertEquals(0L, SyncMerge.highestTime(emptyMap()))
    }
}
