package dev.fitiavana.accounting.features.accounts

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind
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
class AccountRepositoryAprTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: AccountRepository
    private var now = 1_000L

    private val stamps get() = db.syncMetaDao().all().associate { it.key() to it.meta().stamp }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = AccountRepository(db.accountDao(), ChangeTracker.forDatabase(db, "dev") { now })
    }

    @After
    fun tearDown() = db.close()

    private val earn = Account(id = "earn", name = "Earn", type = AccountTypes.ASSET, aprPercent = 5.5, liquidityLevel = LiquidityLevels.VALUES.first())

    @Test
    fun `a positive rate on an asset is saved and nothing else changes`() {
        repository.insert(earn)

        assertTrue(repository.updateApr("earn", 7.25))

        assertEquals(earn.copy(aprPercent = 7.25), repository.getById("earn"))
    }

    @Test
    fun `the change is stamped so it syncs`() {
        repository.insert(earn)
        val before = stamps.getValue(RecordKey(SyncKind.ACCOUNT, "earn"))
        now += 10

        repository.updateApr("earn", 7.25)

        assertTrue(stamps.getValue(RecordKey(SyncKind.ACCOUNT, "earn")) > before)
    }

    @Test
    fun `a rate of zero or less is ignored`() {
        repository.insert(earn)

        assertFalse(repository.updateApr("earn", 0.0))
        assertFalse(repository.updateApr("earn", -1.0))

        assertEquals(5.5, repository.getById("earn")?.aprPercent)
    }

    @Test
    fun `an account that is not an asset is ignored`() {
        repository.insert(earn.copy(id = "debt", type = AccountTypes.LIABILITY, aprPercent = null))

        assertFalse(repository.updateApr("debt", 3.0))

        assertNull(repository.getById("debt")?.aprPercent)
    }

    @Test
    fun `an unknown account is ignored`() {
        assertFalse(repository.updateApr("missing", 3.0))
    }
}
