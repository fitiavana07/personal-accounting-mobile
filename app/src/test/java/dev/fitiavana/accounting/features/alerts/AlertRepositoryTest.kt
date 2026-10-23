package dev.fitiavana.accounting.features.alerts

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Alerts are stamped and tombstoned like every other synced record. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AlertRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var tracker: ChangeTracker
    private lateinit var accounts: AccountRepository
    private lateinit var repository: AlertRepository
    private var now = 1_000L
    private var ids = 0

    private val meta get() = db.syncMetaDao().all().associate { it.key() to it.meta() }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        tracker = ChangeTracker.forDatabase(db, "dev") { now }
        accounts = AccountRepository(db.accountDao(), tracker)
        repository = AlertRepository(db.alertDao(), tracker, clock = { now }, idGenerator = { "id${ids++}" })
        accounts.insert(Account("btc", "BTC", "asset"))
    }

    @After
    fun tearDown() = db.close()

    private fun key(id: String) = RecordKey(SyncKind.ALERT, id)

    @Test
    fun `creating an alert stores and stamps it`() {
        val alert = repository.create("btc", AlertTypes.CROSSING_ABOVE, 12.5)

        assertEquals(alert, repository.get(alert.id))
        assertEquals("btc", alert.accountId)
        assertEquals(12.5, alert.thresholdPercent, 0.0)
        assertEquals(1_000L, alert.createdAt)
        assertEquals(false, meta.getValue(key(alert.id)).deleted)
    }

    @Test
    fun `updating an alert keeps its id and creation time and restamps it`() {
        val alert = repository.create("btc", AlertTypes.CROSSING_ABOVE, 12.5)
        val first = meta.getValue(key(alert.id)).stamp
        now = 2_000L

        repository.update(alert.copy(type = AlertTypes.CROSSING_BELOW, thresholdPercent = -5.0))

        val stored = repository.get(alert.id)!!
        assertEquals(AlertTypes.CROSSING_BELOW, stored.type)
        assertEquals(-5.0, stored.thresholdPercent, 0.0)
        assertEquals(1_000L, stored.createdAt)
        assertNotEquals(first, meta.getValue(key(alert.id)).stamp)
    }

    @Test
    fun `deleting an alert leaves a tombstone`() {
        val alert = repository.create("btc", AlertTypes.CROSSING_ABOVE, 12.5)

        repository.delete(alert.id)

        assertTrue(repository.getAllSync().isEmpty())
        assertEquals(true, meta.getValue(key(alert.id)).deleted)
    }

    @Test
    fun `deleting an account tombstones its alerts`() {
        val alert = repository.create("btc", AlertTypes.CROSSING_ABOVE, 12.5)

        accounts.delete(Account("btc", "BTC", "asset"))

        assertTrue(repository.getAllSync().isEmpty())
        assertEquals(true, meta.getValue(key(alert.id)).deleted)
    }
}
