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
 * Version 27 adds the transactions' nullable `exchange_rate` (the rate of a sell-to-base swap).
 * Room refuses to open a migrated database whose tables differ from the entities, so the
 * column is compared with the one Room generates for a fresh database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class Migration26To27Test {

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

    /** A bare database at [version] whose transactions table has only the columns of version 26. */
    private fun bareDatabase(version: Int): SupportSQLiteDatabase =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `transactions` (`id` TEXT NOT NULL, " +
                                "`createdAt` INTEGER NOT NULL, " +
                                "`transactionDatetime` INTEGER NOT NULL, " +
                                "`note` TEXT NOT NULL, PRIMARY KEY(`id`))"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        ).writableDatabase.also { openDatabases += it }

    private fun rateColumn(db: SupportSQLiteDatabase): List<List<String>> =
        db.query("PRAGMA table_info(`transactions`)").use { c ->
            val rows = mutableListOf<List<String>>()
            while (c.moveToNext()) {
                if (c.getString(1) == "exchange_rate") {
                    // name, type, notnull, pk
                    rows += listOf(c.getString(1), c.getString(2), c.getString(3), c.getString(5))
                }
            }
            rows
        }

    @Test
    fun `a version 26 database gains the exchange_rate column Room expects`() {
        val db = bareDatabase(26)

        AppDatabase.MIGRATION_26_27.migrate(db)

        val expected = rateColumn(generated.openHelper.writableDatabase)
        assertEquals(1, expected.size)
        assertEquals(expected, rateColumn(db))
    }

    @Test
    fun `the migration is harmless when the column is already there`() {
        val db = bareDatabase(26)
        AppDatabase.MIGRATION_26_27.migrate(db)

        AppDatabase.MIGRATION_26_27.migrate(db)

        assertEquals(1, rateColumn(db).size)
    }

    @Test
    fun `the schema version is at least 27`() {
        assertTrue(AppDatabase.SCHEMA_VERSION >= 27)
    }

    @Test
    fun `existing transactions keep their values and have no rate`() {
        val db = bareDatabase(26)
        db.execSQL("INSERT INTO `transactions` VALUES ('t1', 1, 2, 'hello')")

        AppDatabase.MIGRATION_26_27.migrate(db)

        db.query("SELECT `note`, `exchange_rate` FROM `transactions`").use { c ->
            c.moveToFirst()
            assertEquals("hello", c.getString(0))
            assertTrue(c.isNull(1))
        }
    }
}
