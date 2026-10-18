package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.templates.TemplateEntry
import dev.fitiavana.accounting.features.templates.TemplateModes
import dev.fitiavana.accounting.features.templates.TemplateSlots
import dev.fitiavana.accounting.features.templates.TransactionTemplate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncMetaDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: SyncMetaDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        dao = db.syncMetaDao()
    }

    @After
    fun tearDown() = db.close()

    private fun row(kind: SyncKind, id: String, time: Long, device: String = "a", deleted: Boolean = false) =
        SyncMetaRow.of(RecordKey(kind, id), Meta(Stamp(time, device), deleted))

    @Test
    fun `a row converts to its key and meta and back`() {
        val row = row(SyncKind.ACCOUNT, "a1", 5, "dev", deleted = true)

        assertEquals(RecordKey(SyncKind.ACCOUNT, "a1"), row.key())
        assertEquals(Meta(Stamp(5, "dev"), deleted = true), row.meta())
    }

    @Test
    fun `upsert inserts new rows and replaces existing ones by kind and id`() {
        dao.upsert(listOf(row(SyncKind.ACCOUNT, "a1", 1), row(SyncKind.INSTRUMENT, "a1", 1)))

        dao.upsert(listOf(row(SyncKind.ACCOUNT, "a1", 9, "b", deleted = true)))

        val all = dao.all().associate { it.key() to it.meta() }
        assertEquals(2, all.size)
        assertEquals(Meta(Stamp(9, "b"), true), all.getValue(RecordKey(SyncKind.ACCOUNT, "a1")))
        assertEquals(Meta(Stamp(1, "a"), false), all.getValue(RecordKey(SyncKind.INSTRUMENT, "a1")))
    }

    @Test
    fun `highestTime is the largest stamp time or zero when empty`() {
        assertEquals(0L, dao.highestTime())

        dao.upsert(listOf(row(SyncKind.ACCOUNT, "a1", 4), row(SyncKind.ACCOUNT, "a2", 11), row(SyncKind.TEMPLATE, "t", 7)))

        assertEquals(11L, dao.highestTime())
    }

    @Test
    fun `deleteAll empties the table`() {
        dao.upsert(listOf(row(SyncKind.ACCOUNT, "a1", 4)))

        dao.deleteAll()

        assertTrue(dao.all().isEmpty())
    }

    @Test
    fun `templateIdsOfAccount lists the templates that pick the account`() {
        db.accountDao().insert(Account("acc1", "Cash", "asset"))
        db.accountDao().insert(Account("acc2", "Bank", "asset"))
        db.accountDao().insert(Account("acc3", "Other", "asset"))
        db.transactionDao() // keep the schema warm
        val templates = db.templateDao()
        templates.insertTemplate(TransactionTemplate("t1", "One", TemplateModes.TRANSFER, 1))
        templates.insertTemplate(TransactionTemplate("t2", "Two", TemplateModes.TRANSFER, 2))
        templates.insertEntries(
            listOf(
                TemplateEntry("e1", "t1", "acc1", TemplateSlots.FROM, 0),
                TemplateEntry("e2", "t1", "acc2", TemplateSlots.TO, 1),
                TemplateEntry("e3", "t2", "acc2", TemplateSlots.FROM, 0),
                TemplateEntry("e4", "t2", "acc3", TemplateSlots.TO, 1),
            )
        )

        assertEquals(listOf("t1"), dao.templateIdsOfAccount("acc1"))
        assertEquals(setOf("t1", "t2"), dao.templateIdsOfAccount("acc2").toSet())
        assertTrue(dao.templateIdsOfAccount("missing").isEmpty())
    }
}
