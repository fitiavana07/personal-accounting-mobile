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
 * Version 24 adds the settings' `pinnedAccountId` (the asset pinned first on Home).
 * Room refuses to open a migrated database whose tables differ from the
 * entities, so the column is compared with the one Room generates for a fresh database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class Migration23To24Test {

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

    /** A bare database at [version] whose settings table has only the columns of version 23. */
    private fun bareDatabase(version: Int): SupportSQLiteDatabase =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `app_settings` (`id` INTEGER NOT NULL, " +
                                "`monthlyLivingExpenses` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        ).writableDatabase.also { openDatabases += it }

    private fun pinColumn(db: SupportSQLiteDatabase): List<List<String>> =
        db.query("PRAGMA table_info(`app_settings`)").use { c ->
            val rows = mutableListOf<List<String>>()
            while (c.moveToNext()) {
                if (c.getString(1) == "pinnedAccountId") {
                    // name, type, notnull, pk
                    rows += listOf(c.getString(1), c.getString(2), c.getString(3), c.getString(5))
                }
            }
            rows
        }

    @Test
    fun `a version 23 database gains the pinnedAccountId column Room expects`() {
        val db = bareDatabase(23)

        AppDatabase.MIGRATION_23_24.migrate(db)

        val expected = pinColumn(generated.openHelper.writableDatabase)
        assertEquals(1, expected.size)
        assertEquals(expected, pinColumn(db))
    }

    @Test
    fun `the migration is harmless when the column is already there`() {
        val db = bareDatabase(23)
        AppDatabase.MIGRATION_23_24.migrate(db)

        AppDatabase.MIGRATION_23_24.migrate(db)

        assertEquals(1, pinColumn(db).size)
    }

    @Test
    fun `the schema version is 24`() {
        assertEquals(24, AppDatabase.SCHEMA_VERSION)
    }

    @Test
    fun `existing settings keep their values and have no pin`() {
        val db = bareDatabase(23)
        db.execSQL("INSERT INTO `app_settings` (`id`, `monthlyLivingExpenses`) VALUES (0, 500)")

        AppDatabase.MIGRATION_23_24.migrate(db)

        db.query("SELECT `monthlyLivingExpenses`, `pinnedAccountId` FROM `app_settings`").use { c ->
            c.moveToFirst()
            assertEquals(500L, c.getLong(0))
            assertTrue(c.isNull(1))
        }
    }
}
