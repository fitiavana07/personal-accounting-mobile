package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.templates.TemplateEntry
import dev.fitiavana.accounting.features.templates.TemplateModes
import dev.fitiavana.accounting.features.templates.TemplateSlots
import dev.fitiavana.accounting.features.templates.TransactionTemplate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ChangeTrackerTest {
    private lateinit var db: AppDatabase
    private lateinit var tracker: ChangeTracker
    private var now = 1_000L

    private val meta get() = db.syncMetaDao().all().associate { it.key() to it.meta() }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        tracker = ChangeTracker.forDatabase(db, "dev") { now }
    }

    @After
    fun tearDown() = db.close()

    private fun key(kind: SyncKind, id: String) = RecordKey(kind, id)

    @Test
    fun `touching a record stores it live with a fresh stamp`() {
        tracker.touch(SyncKind.ACCOUNT, "a1")

        assertEquals(mapOf(key(SyncKind.ACCOUNT, "a1") to Meta(Stamp(1_000, "dev"), deleted = false)), meta)
    }

    @Test
    fun `touching again gives a higher stamp even if the clock stood still`() {
        tracker.touch(SyncKind.ACCOUNT, "a1")
        val first = meta.getValue(key(SyncKind.ACCOUNT, "a1")).stamp

        tracker.touch(SyncKind.ACCOUNT, "a1")

        assertTrue(meta.getValue(key(SyncKind.ACCOUNT, "a1")).stamp > first)
    }

    @Test
    fun `touching several ids stamps them all`() {
        tracker.touch(SyncKind.TRANSACTION, listOf("t1", "t2"))

        assertEquals(setOf(key(SyncKind.TRANSACTION, "t1"), key(SyncKind.TRANSACTION, "t2")), meta.keys)
        assertTrue(meta.values.none { it.deleted })
    }

    @Test
    fun `deleting leaves a tombstone with a fresh stamp`() {
        tracker.touch(SyncKind.INSTRUMENT, "USD")
        val live = meta.getValue(key(SyncKind.INSTRUMENT, "USD")).stamp

        tracker.deleted(listOf(key(SyncKind.INSTRUMENT, "USD")))

        val tombstone = meta.getValue(key(SyncKind.INSTRUMENT, "USD"))
        assertTrue(tombstone.deleted)
        assertTrue(tombstone.stamp > live)
    }

    @Test
    fun `writing nothing stamps nothing`() {
        tracker.touch(SyncKind.ACCOUNT, emptyList())
        tracker.deleted(emptyList())

        assertTrue(meta.isEmpty())
    }

    @Test
    fun `the clock starts after the highest stamp already stored`() {
        db.syncMetaDao().upsert(listOf(SyncMetaRow.of(key(SyncKind.ACCOUNT, "old"), Meta(Stamp(50_000, "other"), false))))

        tracker.touch(SyncKind.ACCOUNT, "a1")

        assertEquals(Stamp(50_001, "dev"), meta.getValue(key(SyncKind.ACCOUNT, "a1")).stamp)
    }

    @Test
    fun `observing a peer's time pushes later stamps after it`() {
        tracker.observe(9_000)

        tracker.touch(SyncKind.ACCOUNT, "a1")

        assertEquals(Stamp(9_001, "dev"), meta.getValue(key(SyncKind.ACCOUNT, "a1")).stamp)
    }

    @Test
    fun `replaceAll stamps the new live set and tombstones every other live record`() {
        tracker.touch(SyncKind.ACCOUNT, listOf("keep", "gone"))
        val before = meta

        tracker.replaceAll(setOf(key(SyncKind.ACCOUNT, "keep"), key(SyncKind.ACCOUNT, "new")))

        val after = meta
        assertTrue(after.getValue(key(SyncKind.ACCOUNT, "keep")).stamp > before.getValue(key(SyncKind.ACCOUNT, "keep")).stamp)
        assertTrue(!after.getValue(key(SyncKind.ACCOUNT, "new")).deleted)
        assertTrue(after.getValue(key(SyncKind.ACCOUNT, "gone")).deleted)
    }

    @Test
    fun `replaceAll leaves records that were already deleted alone`() {
        tracker.touch(SyncKind.ACCOUNT, "x")
        tracker.deleted(listOf(key(SyncKind.ACCOUNT, "x")))
        val tombstone = meta.getValue(key(SyncKind.ACCOUNT, "x"))

        tracker.replaceAll(emptySet())

        assertEquals(tombstone, meta.getValue(key(SyncKind.ACCOUNT, "x")))
    }

    @Test
    fun `deleting an account tombstones it and restamps the templates that pick it`() {
        db.accountDao().insert(Account("acc1", "Cash", "asset"))
        db.accountDao().insert(Account("acc2", "Bank", "asset"))
        db.templateDao().insertTemplate(TransactionTemplate("t1", "One", TemplateModes.TRANSFER, 1))
        db.templateDao().insertTemplate(TransactionTemplate("t2", "Two", TemplateModes.TRANSFER, 2))
        db.templateDao().insertEntries(
            listOf(
                TemplateEntry("e1", "t1", "acc1", TemplateSlots.FROM, 0),
                TemplateEntry("e2", "t2", "acc2", TemplateSlots.FROM, 0),
            )
        )

        tracker.deletedAccount("acc1")

        assertTrue(meta.getValue(key(SyncKind.ACCOUNT, "acc1")).deleted)
        assertTrue(!meta.getValue(key(SyncKind.TEMPLATE, "t1")).deleted)
        assertEquals(null, meta[key(SyncKind.TEMPLATE, "t2")])
    }

    @Test
    fun `a change is announced after every write`() = runBlocking {
        val announced = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { tracker.changes.first() } }

        tracker.touch(SyncKind.ACCOUNT, "a1")

        assertEquals(Unit, announced.await())
    }

    @Test
    fun `inTransaction returns the block's result and rolls back on failure`() {
        assertEquals(7, tracker.inTransaction { tracker.touch(SyncKind.ACCOUNT, "a1"); 7 })

        assertThrows(IllegalStateException::class.java) {
            tracker.inTransaction {
                tracker.touch(SyncKind.ACCOUNT, "a2")
                error("boom")
            }
        }

        assertEquals(setOf(key(SyncKind.ACCOUNT, "a1")), meta.keys)
    }
}
