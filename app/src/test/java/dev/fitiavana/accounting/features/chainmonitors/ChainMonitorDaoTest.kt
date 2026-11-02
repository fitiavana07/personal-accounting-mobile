package dev.fitiavana.accounting.features.chainmonitors

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ChainMonitorDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ChainMonitorDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        dao = db.chainMonitorDao()
    }

    @After
    fun tearDown() = db.close()

    private fun monitor(id: String, createdAt: Long = 1L, threshold: String = "0") = ChainMonitor(
        id, "Label $id", "0x" + "a".repeat(40), "0x" + "b".repeat(40), "USDC", 6, threshold, 15, createdAt
    )

    @Test
    fun `monitors come back oldest first`() {
        dao.insertAll(listOf(monitor("late", 20), monitor("early", 10)))

        assertEquals(listOf("early", "late"), dao.getAllSync().map { it.id })
    }

    @Test
    fun `get returns one monitor or null`() {
        dao.insert(monitor("m"))

        assertEquals(monitor("m"), dao.getSync("m"))
        assertNull(dao.getSync("other"))
    }

    @Test
    fun `inserting an existing id replaces the monitor`() {
        dao.insert(monitor("m", threshold = "0"))

        dao.insert(monitor("m", threshold = "2.5"))

        assertEquals("2.5", dao.getSync("m")!!.threshold)
        assertEquals(1, dao.getAllSync().size)
    }

    @Test
    fun `the threshold keeps its exact decimal text`() {
        dao.insert(monitor("m", threshold = "0.000001"))

        assertEquals("0.000001", dao.getSync("m")!!.threshold)
    }

    @Test
    fun `states are stored and replaced per monitor`() {
        dao.insert(monitor("m"))
        dao.upsertStates(listOf(MonitorState("m", true, "sig", 5L, "1.5")))

        dao.upsertStates(listOf(MonitorState("m", false, "sig", 9L, "2")))

        assertEquals(listOf(MonitorState("m", false, "sig", 9L, "2")), dao.getAllStatesSync())
    }

    @Test
    fun `deleting a monitor deletes its state`() {
        dao.insert(monitor("m"))
        dao.upsertStates(listOf(MonitorState("m", true, "sig", 5L, null)))

        dao.delete("m")

        assertEquals(emptyList<MonitorState>(), dao.getAllStatesSync())
        assertNull(dao.getSync("m"))
    }

    @Test
    fun `deleteAll empties both tables`() {
        dao.insertAll(listOf(monitor("a"), monitor("b")))
        dao.upsertStates(listOf(MonitorState("a", true, "sig", 5L, null)))

        dao.deleteAll()

        assertEquals(emptyList<ChainMonitor>(), dao.getAllSync())
        assertEquals(emptyList<MonitorState>(), dao.getAllStatesSync())
    }
}
