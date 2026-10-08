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
 * Version 18 shipped with the template tables only, so the accounts' APR column arrives in 18 to 19.
 * Room refuses to open a migrated database whose tables differ from the entities, so the column is
 * compared with the one Room generates for a fresh database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class Migration18To19Test {

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

    private fun columns(db: SupportSQLiteDatabase): List<List<String>> =
        db.query("PRAGMA table_info(`accounts`)").use { c ->
            val rows = mutableListOf<List<String>>()
            while (c.moveToNext()) {
                // name, type, notnull, pk
                rows += listOf(c.getString(1), c.getString(2), c.getString(3), c.getString(5))
            }
            rows.sortedBy { it[0] }
        }

    private fun aprColumn(db: SupportSQLiteDatabase) = columns(db).filter { it[0] == "aprPercent" }

    @Test
    fun `a version 18 database gains the aprPercent column Room expects`() {
        val db = bareDatabase(18)

        AppDatabase.MIGRATION_18_19.migrate(db)

        val expected = aprColumn(generated.openHelper.writableDatabase)
        assertEquals(1, expected.size)
        assertEquals(expected, aprColumn(db))
    }

    @Test
    fun `a version 17 database ends up with the column after both migrations`() {
        val db = bareDatabase(17)

        AppDatabase.MIGRATION_17_18.migrate(db)
        AppDatabase.MIGRATION_18_19.migrate(db)

        assertEquals(1, aprColumn(db).size)
    }

    @Test
    fun `the migration is harmless when the column is already there`() {
        // Covers a database that was already given the column by an earlier build of this change.
        val db = bareDatabase(18)
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `aprPercent` REAL")

        AppDatabase.MIGRATION_18_19.migrate(db)

        assertEquals(1, aprColumn(db).size)
    }

    @Test
    fun `existing accounts keep their rows and get no APR`() {
        val db = bareDatabase(18)
        db.execSQL("INSERT INTO `accounts` (`id`) VALUES ('a1')")

        AppDatabase.MIGRATION_18_19.migrate(db)

        db.query("SELECT `aprPercent` FROM `accounts` WHERE `id` = 'a1'").use { c ->
            c.moveToFirst()
            assertEquals(true, c.isNull(0))
        }
    }

    @Test
    fun `the schema version is 19`() {
        assertEquals(19, AppDatabase.SCHEMA_VERSION)
    }
}
