package dev.fitiavana.accounting.features.instruments

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class InstrumentDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var instrumentDao: InstrumentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        instrumentDao = db.instrumentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun instrument(code: String, note: String = "note-$code") =
        Instrument(code = code, note = note, type = "currency", decimalPlaces = 2)

    @Test
    fun `getAllSync orders instruments by code ascending`() {
        instrumentDao.insert(instrument("USD"))
        instrumentDao.insert(instrument("EUR"))
        instrumentDao.insert(instrument("AUD"))

        val codes = instrumentDao.getAllSync().map { it.code }

        assertEquals(listOf("AUD", "EUR", "USD"), codes)
    }

    @Test
    fun `getByCode returns null when instrument does not exist`() {
        assertNull(instrumentDao.getByCode("USD"))
    }

    @Test
    fun `getByCode returns the matching instrument`() {
        instrumentDao.insert(instrument("USD", note = "US Dollar"))

        assertEquals("US Dollar", instrumentDao.getByCode("USD")?.note)
    }

    @Test
    fun `insert with IGNORE conflict strategy keeps the original row on duplicate code`() {
        instrumentDao.insert(instrument("USD", note = "first"))
        instrumentDao.insert(instrument("USD", note = "second"))

        assertEquals("first", instrumentDao.getByCode("USD")?.note)
        assertEquals(1, instrumentDao.getAllSync().size)
    }

    @Test
    fun `update modifies an existing instrument`() {
        instrumentDao.insert(instrument("USD", note = "before"))

        instrumentDao.update(instrument("USD", note = "after"))

        assertEquals("after", instrumentDao.getByCode("USD")?.note)
    }

    @Test
    fun `delete removes the instrument`() {
        val usd = instrument("USD")
        instrumentDao.insert(usd)

        instrumentDao.delete(usd)

        assertNull(instrumentDao.getByCode("USD"))
    }

    @Test
    fun `deleteAll clears every instrument`() {
        instrumentDao.insertAll(listOf(instrument("USD"), instrument("EUR")))

        instrumentDao.deleteAll()

        assertTrue(instrumentDao.getAllSync().isEmpty())
    }
}
