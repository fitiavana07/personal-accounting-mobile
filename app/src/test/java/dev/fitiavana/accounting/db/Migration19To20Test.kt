package dev.fitiavana.accounting.db

import android.content.Context
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
 * Version 20 adds the `sync_meta` table (the stamp of every synced record). Room refuses to open a
 * migrated database whose tables differ from the entities, so the table is compared with the one
 * Room generates for a fresh database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class Migration19To20Test {

    private lateinit var context: Context
    private lateinit var generated: AppDatabase
    private val openDatabases = mutableListOf<SupportSQLiteDatabase>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        generated = inMemoryDatabase()
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

    private fun columns(db: SupportSQLiteDatabase): List<List<String>> =
        db.query("PRAGMA table_info(`sync_meta`)").use { c ->
            val rows = mutableListOf<List<String>>()
            while (c.moveToNext()) {
                // name, type, notnull, pk position
                rows += listOf(c.getString(1), c.getString(2), c.getString(3), c.getString(5))
            }
            rows.sortedBy { it[0] }
        }

    @Test
    fun `a version 19 database gains the sync_meta table Room expects`() {
        val db = bareDatabase(19)

        AppDatabase.MIGRATION_19_20.migrate(db)

        val expected = columns(generated.openHelper.writableDatabase)
        assertEquals(5, expected.size)
        assertEquals(expected, columns(db))
    }

    @Test
    fun `the migration is harmless when the table is already there`() {
        val db = bareDatabase(19)
        AppDatabase.MIGRATION_19_20.migrate(db)

        AppDatabase.MIGRATION_19_20.migrate(db)

        assertEquals(5, columns(db).size)
    }

    @Test
    fun `the new table starts empty so existing rows read as unstamped`() {
        val db = bareDatabase(19)

        AppDatabase.MIGRATION_19_20.migrate(db)

        db.query("SELECT COUNT(*) FROM `sync_meta`").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun `the schema version is 20`() {
        assertEquals(20, AppDatabase.SCHEMA_VERSION)
        assertTrue(AppDatabase.SCHEMA_VERSION > 19)
    }
}
