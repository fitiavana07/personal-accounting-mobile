package dev.fitiavana.accounting.features.sync

import android.database.sqlite.SQLiteConstraintException
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.alerts.AlertRepository
import dev.fitiavana.accounting.features.alerts.AlertTypes
import dev.fitiavana.accounting.features.backup.BackupRepository
import dev.fitiavana.accounting.features.backup.RestoreResult
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.settings.AppSettingsRepository
import dev.fitiavana.accounting.features.templates.TemplateModes
import dev.fitiavana.accounting.features.templates.TemplateRepository
import dev.fitiavana.accounting.features.templates.TemplateSlot
import dev.fitiavana.accounting.features.templates.TemplateSlots
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.features.transactions.TransactionRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every repository write leaves the right stamp or tombstone, in the same transaction as the write. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class StampedWritesTest {
    private lateinit var db: AppDatabase
    private lateinit var tracker: ChangeTracker
    private lateinit var instruments: InstrumentRepository
    private lateinit var accounts: AccountRepository
    private lateinit var transactions: TransactionRepository
    private lateinit var templates: TemplateRepository
    private lateinit var settings: AppSettingsRepository
    private var now = 1_000L
    private var ids = 0

    private val meta get() = db.syncMetaDao().all().associate { it.key() to it.meta() }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        tracker = ChangeTracker.forDatabase(db, "dev") { now }
        instruments = InstrumentRepository(db.instrumentDao(), db.accountDao(), tracker)
        accounts = AccountRepository(db.accountDao(), tracker)
        transactions = TransactionRepository(db.transactionDao(), tracker)
        templates = TemplateRepository(db.templateDao(), tracker, clock = { 1L }, idGenerator = { "id${ids++}" })
        settings = AppSettingsRepository(db.appSettingsDao(), tracker)
    }

    @After
    fun tearDown() = db.close()

    private fun key(kind: SyncKind, id: String) = RecordKey(kind, id)

    private fun stampOf(kind: SyncKind, id: String) = meta.getValue(key(kind, id)).stamp

    private val usd = Instrument("USD", "US Dollar", "currency", 2)
    private fun account(id: String, instrumentCode: String? = null) =
        Account(id, "Name $id", "asset", instrumentCode = instrumentCode)

    private fun transaction(id: String) = Transaction(id, 1L, 2L, "note")
    private fun entry(id: String, transactionId: String, accountId: String) =
        TransactionEntry(id, transactionId, accountId, debitAmount = 10L, creditAmount = null)

    // --- instruments ---

    @Test
    fun `an instrument is stamped when inserted and restamped when updated`() {
        instruments.insert(usd)
        val first = stampOf(SyncKind.INSTRUMENT, "USD")

        instruments.update(usd.copy(note = "Dollar"))

        assertTrue(stampOf(SyncKind.INSTRUMENT, "USD") > first)
        assertTrue(!meta.getValue(key(SyncKind.INSTRUMENT, "USD")).deleted)
    }

    @Test
    fun `a deleted instrument leaves a tombstone`() {
        instruments.insert(usd)

        instruments.delete(usd)

        assertTrue(meta.getValue(key(SyncKind.INSTRUMENT, "USD")).deleted)
        assertNull(db.instrumentDao().getByCode("USD"))
    }

    @Test
    fun `an instrument that cannot be deleted keeps its live stamp`() {
        instruments.insert(usd)
        accounts.insert(account("a1", "USD"))
        val before = meta

        assertThrows(SQLiteConstraintException::class.java) { instruments.delete(usd) }

        assertEquals(before, meta)
    }

    // --- accounts ---

    @Test
    fun `an account is stamped when inserted and restamped when updated`() {
        accounts.insert(account("a1"))
        val first = stampOf(SyncKind.ACCOUNT, "a1")

        accounts.update(account("a1").copy(name = "Renamed"))

        assertTrue(stampOf(SyncKind.ACCOUNT, "a1") > first)
    }

    @Test
    fun `a deleted account leaves a tombstone and restamps the templates that picked it`() {
        accounts.insert(account("a1"))
        accounts.insert(account("a2"))
        templates.save("Move", TemplateModes.TRANSFER, listOf(TemplateSlot(TemplateSlots.FROM, "a1"), TemplateSlot(TemplateSlots.TO, "a2")))
        val templateId = db.templateDao().getAllTemplatesSync().single().id
        val templateStamp = stampOf(SyncKind.TEMPLATE, templateId)

        accounts.delete(account("a1"))

        assertTrue(meta.getValue(key(SyncKind.ACCOUNT, "a1")).deleted)
        assertTrue(stampOf(SyncKind.TEMPLATE, templateId) > templateStamp)
    }

    @Test
    fun `an account with transactions cannot be deleted and nothing is stamped`() {
        accounts.insert(account("a1"))
        transactions.save(transaction("t1"), listOf(entry("e1", "t1", "a1")))
        val before = meta

        assertThrows(SQLiteConstraintException::class.java) { accounts.delete(account("a1")) }

        assertEquals(before, meta)
        assertEquals("a1", db.accountDao().getById("a1")?.id)
    }

    // --- transactions ---

    @Test
    fun `saving a transaction stores it with its entries under one stamp`() {
        accounts.insert(account("a1"))
        accounts.insert(account("a2"))

        transactions.save(transaction("t1"), listOf(entry("e1", "t1", "a1"), entry("e2", "t1", "a2")))

        assertEquals(2, db.transactionDao().getWithEntries("t1")?.entries?.size)
        assertTrue(!meta.getValue(key(SyncKind.TRANSACTION, "t1")).deleted)
        assertEquals(setOf(key(SyncKind.ACCOUNT, "a1"), key(SyncKind.ACCOUNT, "a2"), key(SyncKind.TRANSACTION, "t1")), meta.keys)
    }

    @Test
    fun `a transaction whose entry fails leaves nothing behind`() {
        accounts.insert(account("a1"))
        val before = meta

        assertThrows(SQLiteConstraintException::class.java) {
            transactions.save(transaction("t1"), listOf(entry("e1", "t1", "a1"), entry("e2", "t1", "missing")))
        }

        assertNull(db.transactionDao().getWithEntries("t1"))
        assertEquals(before, meta)
    }

    @Test
    fun `clearing all transactions tombstones each of them and keeps the accounts`() {
        accounts.insert(account("a1"))
        transactions.save(transaction("t1"), listOf(entry("e1", "t1", "a1")))
        transactions.save(transaction("t2"), listOf(entry("e2", "t2", "a1")))

        transactions.clearAll()

        assertTrue(db.transactionDao().getAllTransactionsSync().isEmpty())
        assertTrue(db.transactionDao().getAllEntriesSync().isEmpty())
        assertTrue(meta.getValue(key(SyncKind.TRANSACTION, "t1")).deleted)
        assertTrue(meta.getValue(key(SyncKind.TRANSACTION, "t2")).deleted)
        assertTrue(!meta.getValue(key(SyncKind.ACCOUNT, "a1")).deleted)
    }

    // --- templates ---

    @Test
    fun `a saved template is stamped and a deleted one leaves a tombstone`() {
        accounts.insert(account("a1"))
        accounts.insert(account("a2"))
        templates.save("Move", TemplateModes.TRANSFER, listOf(TemplateSlot(TemplateSlots.FROM, "a1"), TemplateSlot(TemplateSlots.TO, "a2")))
        val templateId = db.templateDao().getAllTemplatesSync().single().id
        assertTrue(!meta.getValue(key(SyncKind.TEMPLATE, templateId)).deleted)

        templates.delete(templateId)

        assertTrue(meta.getValue(key(SyncKind.TEMPLATE, templateId)).deleted)
        assertTrue(db.templateDao().getAllTemplatesSync().isEmpty())
    }

    // --- settings ---

    @Test
    fun `the living expenses setting is stamped as one record`() {
        settings.setMonthlyLivingExpenses(100)
        val first = stampOf(SyncKind.SETTINGS, "0")

        settings.setMonthlyLivingExpenses(200)

        assertTrue(stampOf(SyncKind.SETTINGS, "0") > first)
        assertEquals(setOf(key(SyncKind.SETTINGS, "0")), meta.keys)
    }

    // --- restore ---

    @Test
    fun `restoring a backup restamps what it brings and tombstones what it replaces`() {
        val backup = BackupRepository(
            db, db.accountDao(), db.instrumentDao(), db.transactionDao(), db.accountBalanceDao(),
            db.exchangeRateCacheDao(), db.appSettingsDao(), db.templateDao(), db.alertDao(), tracker
        )
        val alerts = AlertRepository(db.alertDao(), tracker, clock = { 1L }, idGenerator = { "alert${ids++}" })
        instruments.insert(usd)
        accounts.insert(account("a1", "USD"))
        settings.setMonthlyLivingExpenses(5)
        val kept = alerts.create("a1", AlertTypes.CROSSING_ABOVE, 10.0)
        val json = backup.export()
        val usdStamp = stampOf(SyncKind.INSTRUMENT, "USD")
        val keptStamp = stampOf(SyncKind.ALERT, kept.id)
        instruments.insert(Instrument("EUR", "Euro", "currency", 2))
        accounts.insert(account("extra"))
        val extra = alerts.create("extra", AlertTypes.CROSSING_BELOW, -5.0)

        assertEquals(RestoreResult.Success, backup.restore(json))

        assertTrue(stampOf(SyncKind.ALERT, kept.id) > keptStamp)
        assertTrue(meta.getValue(key(SyncKind.ALERT, extra.id)).deleted)
        assertEquals(listOf(kept.id), alerts.getAllSync().map { it.id })

        assertTrue(stampOf(SyncKind.INSTRUMENT, "USD") > usdStamp)
        assertTrue(!meta.getValue(key(SyncKind.ACCOUNT, "a1")).deleted)
        assertTrue(!meta.getValue(key(SyncKind.SETTINGS, "0")).deleted)
        assertTrue(meta.getValue(key(SyncKind.INSTRUMENT, "EUR")).deleted)
        assertTrue(meta.getValue(key(SyncKind.ACCOUNT, "extra")).deleted)
    }
}
