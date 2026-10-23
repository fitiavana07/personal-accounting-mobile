package dev.fitiavana.accounting.features.alerts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.features.accounts.Account
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AlertDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AlertDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.alertDao()
        db.accountDao().insert(Account(id = "btc", name = "BTC", type = "asset"))
        db.accountDao().insert(Account(id = "eth", name = "ETH", type = "asset"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun alert(id: String, accountId: String = "btc", threshold: Double = 10.0, createdAt: Long = 1L) =
        GainLossAlert(id, accountId, AlertTypes.CROSSING_ABOVE, threshold, createdAt)

    @Test
    fun `alerts are listed oldest first`() {
        dao.insert(alert("late", createdAt = 2L))
        dao.insert(alert("early", createdAt = 1L))

        assertEquals(listOf("early", "late"), dao.getAllSync().map { it.id })
    }

    @Test
    fun `an alert is found by id`() {
        dao.insert(alert("a1", threshold = -5.5))

        assertEquals(-5.5, dao.getSync("a1")!!.thresholdPercent, 0.0)
        assertNull(dao.getSync("missing"))
    }

    @Test
    fun `inserting an alert with an existing id replaces it`() {
        dao.insert(alert("a1", threshold = 10.0))
        dao.insert(alert("a1", threshold = 20.0))

        assertEquals(listOf(20.0), dao.getAllSync().map { it.thresholdPercent })
    }

    @Test
    fun `deleting an alert removes only that alert`() {
        dao.insertAll(listOf(alert("a1"), alert("a2")))

        dao.delete("a1")

        assertEquals(listOf("a2"), dao.getAllSync().map { it.id })
    }

    @Test
    fun `deleteAll removes every alert`() {
        dao.insertAll(listOf(alert("a1"), alert("a2")))

        dao.deleteAll()

        assertTrue(dao.getAllSync().isEmpty())
    }

    @Test
    fun `deleting an account deletes its alerts and their states`() {
        dao.insertAll(listOf(alert("a1", "btc"), alert("a2", "eth")))
        dao.upsertStates(listOf(AlertState("a1", true, "s1"), AlertState("a2", true, "s2")))

        db.accountDao().delete(Account(id = "btc", name = "BTC", type = "asset"))

        assertEquals(listOf("a2"), dao.getAllSync().map { it.id })
        assertEquals(listOf("a2"), dao.getAllStatesSync().map { it.alertId })
    }

    @Test
    fun `a state is replaced by a newer one for the same alert`() {
        dao.insert(alert("a1"))

        dao.upsertStates(listOf(AlertState("a1", true, "s")))
        dao.upsertStates(listOf(AlertState("a1", false, "s")))

        assertEquals(listOf(false), dao.getAllStatesSync().map { it.armed })
    }

    @Test
    fun `deleting an alert deletes its state`() {
        dao.insert(alert("a1"))
        dao.upsertStates(listOf(AlertState("a1", true, "s")))

        dao.delete("a1")

        assertTrue(dao.getAllStatesSync().isEmpty())
    }
}
