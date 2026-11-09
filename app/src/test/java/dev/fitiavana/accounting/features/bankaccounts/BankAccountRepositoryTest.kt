package dev.fitiavana.accounting.features.bankaccounts

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bank accounts keep their own balance; every write is stamped for sync. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class BankAccountRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var tracker: ChangeTracker
    private lateinit var repository: BankAccountRepository
    private var now = 1_000L
    private var ids = 0

    private val meta get() = db.syncMetaDao().all().associate { it.key() to it.meta() }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        tracker = ChangeTracker.forDatabase(db, "dev") { now }
        repository = BankAccountRepository(
            db.bankAccountDao(), tracker, clock = { now }, idGenerator = { "id${ids++}" }
        )
    }

    @After
    fun tearDown() = db.close()

    private fun accountKey(id: String) = RecordKey(SyncKind.BANK_ACCOUNT, id)
    private fun txKey(id: String) = RecordKey(SyncKind.BANK_ACCOUNT_TRANSACTION, id)

    @Test
    fun `a new account starts at zero with no balance date and is stamped`() {
        val account = repository.createAccount("BOA")

        assertEquals(BankAccount("id0", "BOA", 0, null), repository.getAccount(account.id))
        assertEquals(false, meta.getValue(accountKey(account.id)).deleted)
    }

    @Test
    fun `renaming keeps the balance and restamps the account`() {
        val account = repository.createAccount("BOA")
        repository.addTransaction(account.id, 500, "", 5_000)
        val before = meta.getValue(accountKey(account.id)).stamp
        now = 2_000L

        repository.rename(account.id, "BNI")

        val saved = repository.getAccount(account.id)!!
        assertEquals("BNI", saved.name)
        assertEquals(500L, saved.balance)
        assertNotEquals(before, meta.getValue(accountKey(account.id)).stamp)
    }

    @Test
    fun `a credit increases and a debit decreases the balance`() {
        val account = repository.createAccount("BOA")

        repository.addTransaction(account.id, 1_000, "salary", 10_000)
        repository.addTransaction(account.id, -300, "rent", 20_000)

        assertEquals(700L, repository.getAccount(account.id)!!.balance)
    }

    @Test
    fun `the balance may go negative`() {
        val account = repository.createAccount("BOA")

        repository.addTransaction(account.id, -50, "", 10_000)

        assertEquals(-50L, repository.getAccount(account.id)!!.balance)
    }

    @Test
    fun `the balance date is the latest transaction date even when added out of order`() {
        val account = repository.createAccount("BOA")

        repository.addTransaction(account.id, 1, "", 30_000)
        repository.addTransaction(account.id, 1, "", 10_000)

        assertEquals(30_000L, repository.getAccount(account.id)!!.balanceDate)
    }

    @Test
    fun `a transaction is stored with its creation time and stamped without restamping its account`() {
        val account = repository.createAccount("BOA")
        val accountStamp = meta.getValue(accountKey(account.id)).stamp
        now = 2_000L

        val tx = repository.addTransaction(account.id, -75, "coffee", 1_500)

        assertEquals(BankAccountTransaction(tx.id, account.id, -75, "coffee", 2_000, 1_500), tx)
        assertEquals(listOf(tx), db.bankAccountDao().getTransactionsSync(account.id))
        assertEquals(false, meta.getValue(txKey(tx.id)).deleted)
        // The balance is derived from the transactions, so a new one must not win over a rename elsewhere.
        assertEquals(accountStamp, meta.getValue(accountKey(account.id)).stamp)
    }

    @Test
    fun `a zero amount is rejected`() {
        val account = repository.createAccount("BOA")

        try {
            repository.addTransaction(account.id, 0, "", 1)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(emptyList<BankAccountTransaction>(), db.bankAccountDao().getAllTransactionsSync())
    }

    @Test
    fun `a blank name is rejected`() {
        try {
            repository.createAccount("  ")
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertNull(repository.getAccount("id0"))
    }
}
