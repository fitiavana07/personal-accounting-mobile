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
 * Room refuses to open a migrated database whose tables differ from the entities, so the migration's
 * hand-written SQL is compared against the schema Room itself generates for a fresh database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class Migration17To18Test {

    private lateinit var context: Context
    private lateinit var generated: AppDatabase
    private lateinit var migrated: SupportSQLiteDatabase

    private val templateTables = listOf("transaction_templates", "template_entries")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        generated = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // A bare database holding just what the new tables reference, then run the real migration on it.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(17) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE `accounts` (`id` TEXT NOT NULL PRIMARY KEY)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        migrated = helper.writableDatabase
        AppDatabase.MIGRATION_17_18.migrate(migrated)
    }

    @After
    fun tearDown() {
        generated.close()
        migrated.close()
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<List<String>> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val rows = mutableListOf<List<String>>()
            while (c.moveToNext()) {
                // name, type, notnull, pk
                rows += listOf(c.getString(1), c.getString(2), c.getString(3), c.getString(5))
            }
            rows.sortedBy { it[0] }
        }

    private fun foreignKeys(db: SupportSQLiteDatabase, table: String): List<List<String>> =
        db.query("PRAGMA foreign_key_list(`$table`)").use { c ->
            val rows = mutableListOf<List<String>>()
            while (c.moveToNext()) {
                // table, from, to, on_delete
                rows += listOf(c.getString(2), c.getString(3), c.getString(4), c.getString(6))
            }
            rows.sortedBy { it[1] }
        }

    private fun indexedColumns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA index_list(`$table`)").use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) {
                if (c.getString(3) == "c") names += c.getString(1)
            }
            names.flatMap { name ->
                db.query("PRAGMA index_info(`$name`)").use { info ->
                    val cols = mutableListOf<String>()
                    while (info.moveToNext()) cols += info.getString(2)
                    cols
                }
            }.toSet()
        }

    private fun generatedDb(): SupportSQLiteDatabase = generated.openHelper.writableDatabase

    @Test
    fun `accounts gain the aprPercent column Room expects`() {
        val expected = columns(generatedDb(), "accounts").filter { it[0] == "aprPercent" }
        val actual = columns(migrated, "accounts").filter { it[0] == "aprPercent" }

        assertEquals(1, expected.size)
        assertEquals(expected, actual)
    }

    @Test
    fun `template tables have the same columns as Room generates`() {
        templateTables.forEach { table ->
            assertEquals(table, columns(generatedDb(), table), columns(migrated, table))
        }
    }

    @Test
    fun `template tables have the same foreign keys as Room generates`() {
        templateTables.forEach { table ->
            assertEquals(table, foreignKeys(generatedDb(), table), foreignKeys(migrated, table))
        }
    }

    @Test
    fun `template tables have the same indexed columns as Room generates`() {
        templateTables.forEach { table ->
            assertEquals(table, indexedColumns(generatedDb(), table), indexedColumns(migrated, table))
        }
    }
}
