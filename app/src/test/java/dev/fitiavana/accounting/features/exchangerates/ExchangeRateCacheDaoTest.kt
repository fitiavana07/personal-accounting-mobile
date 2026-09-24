package dev.fitiavana.accounting.features.exchangerates

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ExchangeRateCacheDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: ExchangeRateCacheDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.exchangeRateCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun rate(instrumentCode: String, intermediaryCode: String, rate: Double, fetchedAt: Long = 1_000L) =
        ExchangeRateCache(
            pairKey = ExchangeRateCache.pairKey(instrumentCode, intermediaryCode),
            instrumentCode = instrumentCode,
            intermediaryCode = intermediaryCode,
            rate = rate,
            fetchedAt = fetchedAt
        )

    @Test
    fun `upsert inserts a new cache row keyed by pairKey`() {
        dao.upsert(rate("BTC", "USD", 65000.0))

        val all = dao.getAllSync()

        assertEquals(1, all.size)
        assertEquals("BTC:USD", all.single().pairKey)
    }

    @Test
    fun `upsert with REPLACE conflict strategy overwrites the rate for the same pair`() {
        dao.upsert(rate("BTC", "USD", 65000.0, fetchedAt = 1_000L))
        dao.upsert(rate("BTC", "USD", 70000.0, fetchedAt = 2_000L))

        val all = dao.getAllSync()

        assertEquals(1, all.size)
        assertEquals(70000.0, all.single().rate, 0.0)
        assertEquals(2_000L, all.single().fetchedAt)
    }

    @Test
    fun `insertAll upserts multiple distinct pairs independently`() {
        dao.insertAll(listOf(rate("BTC", "USD", 65000.0), rate("EUR", "USD", 1.1)))

        val pairKeys = dao.getAllSync().map { it.pairKey }.toSet()

        assertEquals(setOf("BTC:USD", "EUR:USD"), pairKeys)
    }

    @Test
    fun `deleteAll clears every cached rate`() {
        dao.insertAll(listOf(rate("BTC", "USD", 65000.0), rate("EUR", "USD", 1.1)))

        dao.deleteAll()

        assertTrue(dao.getAllSync().isEmpty())
    }
}
