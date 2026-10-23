package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.alerts.AlertTypes
import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.sync.SyncFixtures.entry
import dev.fitiavana.accounting.features.sync.SyncFixtures.instrument
import dev.fitiavana.accounting.features.sync.SyncFixtures.key
import dev.fitiavana.accounting.features.sync.SyncFixtures.live
import dev.fitiavana.accounting.features.sync.SyncFixtures.transaction
import dev.fitiavana.accounting.features.templates.TemplateModes
import dev.fitiavana.accounting.features.templates.TemplateSlot
import dev.fitiavana.accounting.features.templates.TemplateSlots
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncRepositoryTest {
    private lateinit var a: SyncDevice
    private lateinit var b: SyncDevice
    private val open = mutableListOf<SyncDevice>()

    @Before
    fun setUp() {
        a = device("a", 1_000)
        b = device("b", 2_000)
    }

    @After
    fun tearDown() = open.forEach { it.close() }

    private fun device(id: String, now: Long, failOnRecalculate: Boolean = false) =
        SyncDevice(id, now, failOnRecalculate).also { open += it }

    private fun bank(id: String) = Account(id, "Bank $id", "asset")
    private fun revenue(id: String) = Account(id, "Revenue $id", "revenue")

    private fun assertInSync() = assertEquals(a.state(), b.state())

    /** An instrument, two accounts and a transaction of 100 between them, recorded on [device]. */
    private fun seedLedger(device: SyncDevice) = device.seedLedger()

    // --- snapshot ---

    @Test
    fun `a snapshot holds every row with its stamp and the tombstones`() {
        seedLedger(a)
        a.accounts.insert(bank("old"))
        a.accounts.delete(bank("old"))

        val snapshot = a.sync.snapshot()

        assertEquals(listOf("a1", "a2"), snapshot.data.accounts.map { it.id }.sorted())
        assertEquals(2, snapshot.data.transactionEntries.size)
        assertTrue(snapshot.meta.getValue(key(SyncKind.ACCOUNT, "old")).deleted)
        assertEquals(live(a.stampOf(SyncKind.ACCOUNT, "a1").time, "a"), snapshot.meta.getValue(key(SyncKind.ACCOUNT, "a1")))
        assertNull(snapshot.violation())
    }

    @Test
    fun `rows from before sync existed read as unstamped, older than any real write`() {
        a.db.accountDao().insert(bank("legacy"))

        val snapshot = a.sync.snapshot()

        assertEquals(Meta(Stamp(0, ""), deleted = false), snapshot.meta.getValue(key(SyncKind.ACCOUNT, "legacy")))
    }

    // --- propagation ---

    @Test
    fun `a ledger recorded on one device appears on the other with its balances`() {
        seedLedger(a)
        a.settings.setMonthlyLivingExpenses(500)

        assertEquals(ApplyResult.Applied(changed = true), b.sync.apply(a.sync.snapshot()))

        assertEquals(a.state().first, b.state().first)
        assertEquals(AppSettings(monthlyLivingExpenses = 500), b.db.appSettingsDao().getSync())
        assertEquals(100L, b.db.accountBalanceDao().getByAccountId("a1")?.balance)
        assertEquals(100L, b.db.accountBalanceDao().getByAccountId("a2")?.balance)
        assertEquals(2, b.db.transactionDao().getWithEntries("t1")?.entries?.size)
    }

    @Test
    fun `after a session both devices hold identical rows and stamps`() {
        seedLedger(a)
        b.accounts.insert(bank("b1"))

        SyncDevice.sync(initiator = a, responder = b)

        assertInSync()
        assertEquals(setOf("a1", "a2", "b1"), a.db.accountDao().getAllSync().map { it.id }.toSet())
    }

    @Test
    fun `changes travel on through a third device`() {
        val c = device("c", 3_000)
        seedLedger(a)

        SyncDevice.sync(a, b)
        SyncDevice.sync(b, c)

        assertEquals(a.state().first.transactions, c.state().first.transactions)
        assertEquals(100L, c.db.accountBalanceDao().getByAccountId("a1")?.balance)
    }

    @Test
    fun `applying the same snapshot twice changes nothing the second time`() {
        seedLedger(a)
        val snapshot = a.sync.snapshot()
        b.sync.apply(snapshot)
        val before = b.state()

        assertEquals(ApplyResult.Applied(changed = false), b.sync.apply(snapshot))

        assertEquals(before, b.state())
    }

    // --- last write wins ---

    @Test
    fun `the later of two concurrent edits wins on both devices`() {
        a.accounts.insert(bank("a1"))
        SyncDevice.sync(a, b)
        a.now = 3_000
        a.accounts.update(bank("a1").copy(name = "From A"))
        b.now = 4_000
        b.accounts.update(bank("a1").copy(name = "From B"))

        SyncDevice.sync(a, b)

        assertEquals("From B", a.db.accountDao().getById("a1")?.name)
        assertEquals("From B", b.db.accountDao().getById("a1")?.name)
        assertInSync()
    }

    @Test
    fun `a later edit brings back a record deleted elsewhere`() {
        a.accounts.insert(bank("a1"))
        SyncDevice.sync(a, b)
        a.now = 3_000
        a.accounts.delete(bank("a1"))
        b.now = 4_000
        b.accounts.update(bank("a1").copy(name = "Edited"))

        SyncDevice.sync(a, b)

        assertEquals("Edited", a.db.accountDao().getById("a1")?.name)
        assertInSync()
    }

    @Test
    fun `a later deletion removes a record edited elsewhere`() {
        a.accounts.insert(bank("a1"))
        SyncDevice.sync(a, b)
        b.now = 3_000
        b.accounts.update(bank("a1").copy(name = "Edited"))
        a.now = 4_000
        a.accounts.delete(bank("a1"))

        SyncDevice.sync(a, b)

        assertNull(b.db.accountDao().getById("a1"))
        assertInSync()
    }

    @Test
    fun `local rows from before sync lose to any stamped version of the same record`() {
        a.db.accountDao().insert(bank("a1").copy(name = "Legacy"))
        b.accounts.insert(bank("a1").copy(name = "Stamped"))

        SyncDevice.sync(a, b)

        assertEquals("Stamped", a.db.accountDao().getById("a1")?.name)
        assertInSync()
    }

    // --- deletes ---

    @Test
    fun `clearing all transactions on one device empties the other and zeroes its balances`() {
        seedLedger(a)
        SyncDevice.sync(a, b)
        a.now = 5_000
        a.transactions.clearAll()
        a.balances.recalculateAll()

        SyncDevice.sync(a, b)

        assertTrue(b.db.transactionDao().getAllTransactionsSync().isEmpty())
        assertTrue(b.db.transactionDao().getAllEntriesSync().isEmpty())
        assertEquals(0L, b.db.accountBalanceDao().getByAccountId("a1")?.balance)
        assertInSync()
    }

    @Test
    fun `deleting more rows than SQLite takes variables in one statement works`() {
        val many = (1..1_200).map { bank("acc$it") }
        a.db.accountDao().insertAll(many)
        a.tracker.touch(SyncKind.ACCOUNT, many.map { it.id })
        SyncDevice.sync(a, b)
        assertEquals(1_200, b.db.accountDao().getAllSync().size)
        a.now = 5_000
        a.db.accountDao().deleteAll()
        a.tracker.deleted(many.map { key(SyncKind.ACCOUNT, it.id) })

        SyncDevice.sync(a, b)

        assertTrue(b.db.accountDao().getAllSync().isEmpty())
    }

    @Test
    fun `a deleted instrument and account follow, children before parents`() {
        a.instruments.insert(instrument("USD"))
        a.accounts.insert(bank("a1").copy(instrumentCode = "USD"))
        SyncDevice.sync(a, b)
        a.now = 5_000
        a.accounts.delete(bank("a1"))
        a.instruments.delete(instrument("USD"))

        SyncDevice.sync(a, b)

        assertNull(b.db.accountDao().getById("a1"))
        assertNull(b.db.instrumentDao().getByCode("USD"))
        assertInSync()
    }

    @Test
    fun `a template follows its account being deleted`() {
        a.accounts.insert(bank("a1"))
        a.accounts.insert(bank("a2"))
        a.templates.save("Move", TemplateModes.CLASSIC, listOf(TemplateSlot(TemplateSlots.ROW, "a1"), TemplateSlot(TemplateSlots.ROW, "a2")))
        SyncDevice.sync(a, b)
        a.now = 5_000
        a.accounts.delete(bank("a2"))

        SyncDevice.sync(a, b)

        assertEquals(1, b.db.templateDao().getAllEntriesSync().size)
        assertNull(b.db.accountDao().getById("a2"))
        assertInSync()
    }

    // --- alerts ---

    @Test
    fun `an alert created on one device reaches the other`() {
        a.accounts.insert(bank("a1"))
        val alert = a.alerts.create("a1", AlertTypes.CROSSING_BELOW, -10.0)

        SyncDevice.sync(a, b)

        assertEquals(listOf(alert), b.alerts.getAllSync())
        assertInSync()
    }

    @Test
    fun `an alert edited on one device updates the other`() {
        a.accounts.insert(bank("a1"))
        val alert = a.alerts.create("a1", AlertTypes.CROSSING_ABOVE, 10.0)
        SyncDevice.sync(a, b)
        a.now = 5_000

        a.alerts.update(alert.copy(thresholdPercent = 25.0))
        SyncDevice.sync(a, b)

        assertEquals(25.0, b.alerts.get(alert.id)!!.thresholdPercent, 0.0)
        assertInSync()
    }

    @Test
    fun `an alert deleted on one device is deleted on the other`() {
        a.accounts.insert(bank("a1"))
        val alert = a.alerts.create("a1", AlertTypes.CROSSING_ABOVE, 10.0)
        SyncDevice.sync(a, b)
        a.now = 5_000

        a.alerts.delete(alert.id)
        SyncDevice.sync(a, b)

        assertTrue(b.alerts.getAllSync().isEmpty())
        assertInSync()
    }

    @Test
    fun `an alert follows its account being deleted`() {
        a.accounts.insert(bank("a1"))
        a.alerts.create("a1", AlertTypes.CROSSING_ABOVE, 10.0)
        SyncDevice.sync(a, b)
        a.now = 5_000
        a.accounts.delete(bank("a1"))

        SyncDevice.sync(a, b)

        assertTrue(b.alerts.getAllSync().isEmpty())
        assertNull(b.db.accountDao().getById("a1"))
        assertInSync()
    }

    @Test
    fun `an alert made on an account that another device deleted is dropped on both`() {
        a.accounts.insert(bank("a1"))
        SyncDevice.sync(a, b)
        a.now = 5_000
        a.accounts.delete(bank("a1"))
        b.now = 6_000
        b.alerts.create("a1", AlertTypes.CROSSING_ABOVE, 10.0)

        SyncDevice.sync(a, b)
        SyncDevice.sync(a, b)

        assertTrue(a.alerts.getAllSync().isEmpty())
        assertTrue(b.alerts.getAllSync().isEmpty())
        assertInSync()
    }

    // --- repair ---

    @Test
    fun `an account on an instrument deleted elsewhere keeps its data and loses the reference`() {
        a.instruments.insert(instrument("USD"))
        SyncDevice.sync(a, b)
        a.now = 3_000
        a.instruments.delete(instrument("USD"))
        b.now = 2_500
        b.accounts.insert(bank("a1").copy(instrumentCode = "USD"))

        SyncDevice.sync(a, b)

        assertNull(b.db.instrumentDao().getByCode("USD"))
        assertEquals(null, b.db.accountDao().getById("a1")?.instrumentCode)
        assertEquals(null, a.db.accountDao().getById("a1")?.instrumentCode)
        assertTrue(b.stampOf(SyncKind.ACCOUNT, "a1") > Stamp(3_000, "a"))
        assertInSync()
    }

    @Test
    fun `a transaction on an account deleted elsewhere is dropped and tombstoned`() {
        a.accounts.insert(bank("a1"))
        SyncDevice.sync(a, b)
        a.now = 3_000
        a.accounts.delete(bank("a1"))
        b.now = 2_500
        b.transactions.save(transaction("t1"), listOf(entry("e1", "t1", "a1")))

        SyncDevice.sync(a, b)

        assertNull(b.db.accountDao().getById("a1"))
        assertTrue(b.db.transactionDao().getAllTransactionsSync().isEmpty())
        assertTrue(b.meta.getValue(key(SyncKind.TRANSACTION, "t1")).deleted)
        assertTrue(b.stampOf(SyncKind.TRANSACTION, "t1") > Stamp(3_000, "a"))
        assertInSync()
    }

    // --- stamps, announcements ---

    @Test
    fun `receiving rows is not a local change and announces nothing`() = runBlocking {
        seedLedger(a)
        val announced = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(300) { b.tracker.changes.first() } }

        b.sync.apply(a.sync.snapshot())

        assertNull(announced.await())
    }

    @Test
    fun `a repair is a local write and is announced once`() = runBlocking {
        a.instruments.insert(instrument("USD"))
        SyncDevice.sync(a, b)
        a.now = 3_000
        a.instruments.delete(instrument("USD"))
        b.accounts.insert(bank("a1").copy(instrumentCode = "USD"))
        val announced = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(2_000) { b.tracker.changes.first() } }

        b.sync.apply(a.sync.snapshot())

        assertEquals(Unit, announced.await())
    }

    @Test
    fun `writes after an apply are newer than everything received`() {
        a.now = 50_000
        a.accounts.insert(bank("a1"))
        b.sync.apply(a.sync.snapshot())

        b.accounts.update(bank("a1").copy(name = "Later"))

        assertTrue(b.stampOf(SyncKind.ACCOUNT, "a1") > a.stampOf(SyncKind.ACCOUNT, "a1"))
    }

    // --- rejection ---

    @Test
    fun `a snapshot of another database version is refused and changes nothing`() {
        seedLedger(a)
        val other = a.sync.snapshot().let { it.copy(data = it.data.copy(schemaVersion = it.data.schemaVersion - 1)) }
        val before = b.state()

        val result = b.sync.apply(other)

        assertTrue(result is ApplyResult.Rejected)
        assertTrue((result as ApplyResult.Rejected).message.contains("version"))
        assertEquals(before, b.state())
    }

    @Test
    fun `a snapshot with a row that has no stamp is refused`() {
        seedLedger(a)
        val broken = a.sync.snapshot().let { it.copy(meta = it.meta - key(SyncKind.ACCOUNT, "a1")) }

        assertTrue(b.sync.apply(broken) is ApplyResult.Rejected)
        assertTrue(b.db.accountDao().getAllSync().isEmpty())
    }

    @Test
    fun `a failure halfway rolls everything back, stamps included`() {
        val failing = device("f", 1_000, failOnRecalculate = true)
        seedLedger(a)
        val before = failing.state()

        val result = failing.sync.apply(a.sync.snapshot())

        assertTrue(result is ApplyResult.Rejected)
        assertEquals(before, failing.state())
        assertTrue(failing.db.accountDao().getAllSync().isEmpty())
        assertFalse(failing.meta.isNotEmpty())
    }
}
