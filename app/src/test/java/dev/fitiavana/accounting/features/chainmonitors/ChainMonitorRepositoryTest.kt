package dev.fitiavana.accounting.features.chainmonitors

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Monitors are stamped and tombstoned like every other synced record; their state is not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ChainMonitorRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var tracker: ChangeTracker
    private lateinit var repository: ChainMonitorRepository
    private var now = 1_000L
    private var ids = 0

    private val meta get() = db.syncMetaDao().all().associate { it.key() to it.meta() }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        tracker = ChangeTracker.forDatabase(db, "dev") { now }
        repository = ChainMonitorRepository(
            db.chainMonitorDao(), tracker, clock = { now }, idGenerator = { "id${ids++}" }
        )
    }

    @After
    fun tearDown() = db.close()

    private fun key(id: String) = RecordKey(SyncKind.CHAIN_MONITOR, id)

    private fun create(label: String = "Acres 40 available", threshold: String = "0") = repository.create(
        label, "0xB99B6dF96d4d5448cC0a5B3e0ef7896df9507Cf5", "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
        "USDC", 6, threshold, 15
    )

    @Test
    fun `creating a monitor stores and stamps it`() {
        val monitor = create()

        assertEquals(monitor, repository.get(monitor.id))
        assertEquals("Acres 40 available", monitor.label)
        assertEquals(1_000L, monitor.createdAt)
        assertEquals(false, meta.getValue(key(monitor.id)).deleted)
    }

    @Test
    fun `updating a monitor keeps its id and creation time and restamps it`() {
        val monitor = create()
        val before = meta.getValue(key(monitor.id)).stamp
        now = 2_000L

        repository.update(monitor.copy(label = "Renamed", threshold = "5"))

        val saved = repository.get(monitor.id)!!
        assertEquals("Renamed", saved.label)
        assertEquals("5", saved.threshold)
        assertEquals(1_000L, saved.createdAt)
        assertNotEquals(before, meta.getValue(key(monitor.id)).stamp)
    }

    @Test
    fun `deleting a monitor removes it and leaves a tombstone`() {
        val monitor = create()
        now = 2_000L

        repository.delete(monitor.id)

        assertNull(repository.get(monitor.id))
        assertEquals(true, meta.getValue(key(monitor.id)).deleted)
    }

    @Test
    fun `deleting a monitor deletes its device-local state`() {
        val monitor = create()
        repository.saveStates(listOf(MonitorState(monitor.id, true, monitor.signature, 1L, null)))

        repository.delete(monitor.id)

        assertTrue(repository.getStatesSync().isEmpty())
    }

    @Test
    fun `saving states is not stamped`() {
        val monitor = create()
        val before = meta

        repository.saveStates(listOf(MonitorState(monitor.id, false, monitor.signature, 7L, "3")))

        assertEquals(before, meta)
        assertEquals("3", repository.getStatesSync().single().lastBalance)
    }
}
