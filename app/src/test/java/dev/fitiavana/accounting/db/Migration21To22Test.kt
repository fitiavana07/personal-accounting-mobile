package dev.fitiavana.accounting.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Version 22 adds the accounts' `linkedRevenueAccountId` (pre-associated revenue account).
 * Room refuses to open a migrated database whose tables differ from the
 * entities, so the column is compared with the one Room generates for a fresh database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class Migration21To22Test {

    private lateinit var context: Context
    private lateinit var generated: AppDatabase
    private val openDatabases = mutableListOf<SupportSQLiteDatabase>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        generated = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        generated.close()
        openDatabases.forEach { it.close() }
    }

    /** A bare database at [version] whose accounts table has only an id, as a stand-in for the real one. */
    private fun bareDatabase(version: Int): SupportSQLiteDatabase =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE `accounts` (`id` TEXT NOT NULL PRIMARY KEY)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        ).writableDatabase.also { openDatabases += it }

    private fun flagColumn(db: SupportSQLiteDatabase): List<List<String>> =
        db.query("PRAGMA table_info(`accounts`)").use { c ->
            val rows = mutableListOf<List<String>>()
            while (c.moveToNext()) {
                if (c.getString(1) == "linkedRevenueAccountId") {
                    // name, type, notnull, pk
                    rows += listOf(c.getString(1), c.getString(2), c.getString(3), c.getString(5))
                }
            }
            rows
        }

    @Test
    fun `a version 21 database gains the linkedRevenueAccountId column Room expects`() {
        val db = bareDatabase(21)

        AppDatabase.MIGRATION_21_22.migrate(db)

        val expected = flagColumn(generated.openHelper.writableDatabase)
        assertEquals(1, expected.size)
        assertEquals(expected, flagColumn(db))
    }

    @Test
    fun `the migration is harmless when the column is already there`() {
        val db = bareDatabase(21)
        AppDatabase.MIGRATION_21_22.migrate(db)

        AppDatabase.MIGRATION_21_22.migrate(db)

        assertEquals(1, flagColumn(db).size)
    }

    @Test
    fun `existing accounts keep their rows and have no linked revenue account`() {
        val db = bareDatabase(21)
        db.execSQL("INSERT INTO `accounts` (`id`) VALUES ('a1')")

        AppDatabase.MIGRATION_21_22.migrate(db)

        db.query("SELECT `linkedRevenueAccountId` FROM `accounts` WHERE `id` = 'a1'").use { c ->
            c.moveToFirst()
            assertTrue(c.isNull(0))
        }
    }

    @Test
    fun `the schema version is 22`() {
        assertEquals(22, AppDatabase.SCHEMA_VERSION)
    }
}
