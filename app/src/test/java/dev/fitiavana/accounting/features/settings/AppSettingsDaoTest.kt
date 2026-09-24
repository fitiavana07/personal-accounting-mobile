package dev.fitiavana.accounting.features.settings

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AppSettingsDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: AppSettingsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.appSettingsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun <T> LiveData<T>.getOrAwaitValue(): T {
        var data: T? = null
        val observer = Observer<T> { data = it }
        observeForever(observer)
        try {
            @Suppress("UNCHECKED_CAST")
            return data as T
        } finally {
            removeObserver(observer)
        }
    }

    @Test
    fun `getSync returns null before any settings are saved`() {
        assertNull(dao.getSync())
    }

    @Test
    fun `upsert stores settings retrievable by getSync`() {
        dao.upsert(AppSettings(monthlyLivingExpenses = 500_000L))

        assertEquals(500_000L, dao.getSync()?.monthlyLivingExpenses)
    }

    @Test
    fun `upsert with REPLACE conflict strategy overwrites the singleton row rather than adding a second`() {
        dao.upsert(AppSettings(monthlyLivingExpenses = 500_000L))
        dao.upsert(AppSettings(monthlyLivingExpenses = 750_000L))

        assertEquals(750_000L, dao.getSync()?.monthlyLivingExpenses)
    }

    @Test
    fun `observe LiveData reflects the current settings`() {
        dao.upsert(AppSettings(monthlyLivingExpenses = 500_000L))

        assertEquals(500_000L, dao.observe().getOrAwaitValue()?.monthlyLivingExpenses)
    }
}
