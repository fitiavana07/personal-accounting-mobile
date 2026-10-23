package dev.fitiavana.accounting.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Version 23 adds the gain/loss alerts and their device-local state. Room refuses to open a
 * migrated database whose tables differ from the entities, so both tables are compared with the
 * ones Room generates for a fresh database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class Migration22To23Test {

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

    private fun columns(db: SupportSQLiteDatabase, table: String): List<List<String>> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val rows = mutableListOf<List<String>>()
            while (c.moveToNext()) {
                // name, type, notnull, pk position
                rows += listOf(c.getString(1), c.getString(2), c.getString(3), c.getString(5))
            }
            rows.sortedBy { it[0] }
        }

    private fun foreignKeys(db: SupportSQLiteDatabase, table: String): List<List<String>> =
        db.query("PRAGMA foreign_key_list(`$table`)").use { c ->
            val rows = mutableListOf<List<String>>()
            while (c.moveToNext()) {
                // referenced table, from, to, on delete
                rows += listOf(c.getString(2), c.getString(3), c.getString(4), c.getString(6))
            }
            rows.sortedBy { it[0] }
        }

    private fun indexColumns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA index_list(`$table`)").use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) {
                if (c.getString(3) == "c") names += c.getString(1)
            }
            names.sorted()
        }

    @Test
    fun `a version 22 database gains the tables Room expects`() {
        val db = bareDatabase(22)

        AppDatabase.MIGRATION_22_23.migrate(db)

        val room = generated.openHelper.writableDatabase
        for (table in listOf("gain_loss_alerts", "alert_states")) {
            val expected = columns(room, table)
            assertEquals(table, true, expected.isNotEmpty())
            assertEquals(table, expected, columns(db, table))
            assertEquals(table, foreignKeys(room, table), foreignKeys(db, table))
            assertEquals(table, indexColumns(room, table), indexColumns(db, table))
        }
    }

    @Test
    fun `the migration is harmless when the tables are already there`() {
        val db = bareDatabase(22)
        AppDatabase.MIGRATION_22_23.migrate(db)

        AppDatabase.MIGRATION_22_23.migrate(db)

        assertEquals(5, columns(db, "gain_loss_alerts").size)
    }

    @Test
    fun `the schema version is 23`() {
        assertEquals(23, AppDatabase.SCHEMA_VERSION)
    }
}
