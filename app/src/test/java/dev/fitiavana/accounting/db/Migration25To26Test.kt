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
 * Version 26 adds the bank accounts and their transactions. Room refuses to open a migrated
 * database whose tables differ from the entities, so both tables (and the index) are compared with
 * the ones Room generates for a fresh database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class Migration25To26Test {

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

    private fun indexes(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA index_list(`$table`)").use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names += c.getString(1)
            names.filter { !it.startsWith("sqlite_autoindex") }.sorted()
        }

    @Test
    fun `a version 25 database gains the tables Room expects`() {
        val db = bareDatabase(25)

        AppDatabase.MIGRATION_25_26.migrate(db)

        val room = generated.openHelper.writableDatabase
        for (table in listOf("bank_accounts", "bank_account_transactions")) {
            val expected = columns(room, table)
            assertEquals(table, true, expected.isNotEmpty())
            assertEquals(table, expected, columns(db, table))
            assertEquals(table, foreignKeys(room, table), foreignKeys(db, table))
            assertEquals(table, indexes(room, table), indexes(db, table))
        }
    }

    @Test
    fun `the migration is harmless when the tables are already there`() {
        val db = bareDatabase(25)
        AppDatabase.MIGRATION_25_26.migrate(db)

        AppDatabase.MIGRATION_25_26.migrate(db)

        assertEquals(4, columns(db, "bank_accounts").size)
    }
}
