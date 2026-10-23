package dev.fitiavana.accounting.features.settings

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind
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
class AppSettingsRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: AppSettingsRepository
    private var now = 1_000L

    private val stored get() = db.appSettingsDao().getSync()
    private val stamp
        get() = db.syncMetaDao().all().single { it.key() == RecordKey(SyncKind.SETTINGS, "0") }.meta().stamp

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = AppSettingsRepository(db.appSettingsDao(), ChangeTracker.forDatabase(db, "dev") { now })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `nothing is pinned by default`() {
        assertNull(stored?.pinnedAccountId)
    }

    @Test
    fun `toggling an account pins it`() {
        repository.togglePin("a1")

        assertEquals("a1", stored?.pinnedAccountId)
    }

    @Test
    fun `toggling another account moves the pin`() {
        repository.togglePin("a1")

        repository.togglePin("a2")

        assertEquals("a2", stored?.pinnedAccountId)
    }

    @Test
    fun `toggling the pinned account unpins it`() {
        repository.togglePin("a1")

        repository.togglePin("a1")

        assertNull(stored?.pinnedAccountId)
    }

    @Test
    fun `pinning stamps the settings record`() {
        repository.setMonthlyLivingExpenses(100)
        val first = stamp
        now += 1

        repository.togglePin("a1")

        assertTrue(stamp > first)
    }

    @Test
    fun `pinning keeps the living expenses`() {
        repository.setMonthlyLivingExpenses(100)

        repository.togglePin("a1")

        assertEquals(100L, stored?.monthlyLivingExpenses)
    }

    @Test
    fun `setting the living expenses keeps the pin`() {
        repository.togglePin("a1")

        repository.setMonthlyLivingExpenses(100)

        assertEquals("a1", stored?.pinnedAccountId)
        assertEquals(100L, stored?.monthlyLivingExpenses)
    }
}
