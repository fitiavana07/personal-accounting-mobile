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
 * Version 25 adds the chain monitors and their device-local state. Room refuses to open a migrated
 * database whose tables differ from the entities, so both tables are compared with the ones Room
 * generates for a fresh database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class Migration24To25Test {

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

    private fun bareDatabase(version: Int): SupportSQLiteDatabase =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

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

    @Test
    fun `a version 24 database gains the tables Room expects`() {
        val db = bareDatabase(24)

        AppDatabase.MIGRATION_24_25.migrate(db)

        val room = generated.openHelper.writableDatabase
        for (table in listOf("chain_monitors", "chain_monitor_states")) {
            val expected = columns(room, table)
            assertEquals(table, true, expected.isNotEmpty())
            assertEquals(table, expected, columns(db, table))
            assertEquals(table, foreignKeys(room, table), foreignKeys(db, table))
        }
    }

    @Test
    fun `the migration is harmless when the tables are already there`() {
        val db = bareDatabase(24)
        AppDatabase.MIGRATION_24_25.migrate(db)

        AppDatabase.MIGRATION_24_25.migrate(db)

        assertEquals(9, columns(db, "chain_monitors").size)
    }
}
