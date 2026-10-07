package dev.fitiavana.accounting.features.templates

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class TemplateDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var dao: TemplateDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = db.accountDao()
        dao = db.templateDao()
        accountDao.insert(Account(id = "cash", name = "Cash", type = "asset"))
        accountDao.insert(Account(id = "bank", name = "Bank", type = "asset"))
        accountDao.insert(Account(id = "food", name = "Food", type = "expense"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun template(id: String, name: String, mode: String = TemplateModes.SIMPLE_TRANSFER, createdAt: Long = 1L) =
        TransactionTemplate(id = id, name = name, mode = mode, createdAt = createdAt)

    private fun entry(id: String, templateId: String, accountId: String, slot: String, position: Int) =
        TemplateEntry(id = id, templateId = templateId, accountId = accountId, slot = slot, position = position)

    @Test
    fun `a saved template is returned with its entries`() {
        dao.insertTemplate(template("t1", "Withdraw"))
        dao.insertEntries(
            listOf(
                entry("e1", "t1", "bank", TemplateSlots.FROM, 0),
                entry("e2", "t1", "cash", TemplateSlots.TO, 1)
            )
        )

        val result = dao.getWithEntriesSync("t1")

        assertEquals("Withdraw", result?.template?.name)
        assertEquals(setOf("e1", "e2"), result?.entries?.map { it.id }?.toSet())
    }

    @Test
    fun `getWithEntriesSync returns null for an unknown id`() {
        assertNull(dao.getWithEntriesSync("nope"))
    }

    @Test
    fun `all templates are listed newest first`() {
        dao.insertTemplate(template("old", "Old", createdAt = 100L))
        dao.insertTemplate(template("new", "New", createdAt = 300L))
        dao.insertTemplate(template("mid", "Mid", createdAt = 200L))

        assertEquals(listOf("new", "mid", "old"), dao.getAllWithEntriesSync().map { it.template.id })
    }

    @Test
    fun `deleting a template removes its entries too`() {
        dao.insertTemplate(template("t1", "Withdraw"))
        dao.insertEntries(listOf(entry("e1", "t1", "bank", TemplateSlots.FROM, 0)))

        dao.deleteTemplate("t1")

        assertNull(dao.getWithEntriesSync("t1"))
        assertTrue(dao.getAllEntriesSync().isEmpty())
    }

    @Test
    fun `deleting an account removes its template entries but not the template`() {
        dao.insertTemplate(template("t1", "Withdraw"))
        dao.insertEntries(
            listOf(
                entry("e1", "t1", "bank", TemplateSlots.FROM, 0),
                entry("e2", "t1", "cash", TemplateSlots.TO, 1)
            )
        )

        accountDao.delete(Account(id = "bank", name = "Bank", type = "asset"))

        val remaining = dao.getWithEntriesSync("t1")
        assertEquals(listOf("e2"), remaining?.entries?.map { it.id })
    }

    @Test
    fun `getAllTemplatesSync and getAllEntriesSync return every row for backups`() {
        dao.insertTemplate(template("t1", "A"))
        dao.insertTemplate(template("t2", "B"))
        dao.insertEntries(listOf(entry("e1", "t1", "bank", TemplateSlots.FROM, 0)))

        assertEquals(setOf("t1", "t2"), dao.getAllTemplatesSync().map { it.id }.toSet())
        assertEquals(listOf("e1"), dao.getAllEntriesSync().map { it.id })
    }

    @Test
    fun `deleteAll helpers empty both tables`() {
        dao.insertTemplate(template("t1", "A"))
        dao.insertEntries(listOf(entry("e1", "t1", "bank", TemplateSlots.FROM, 0)))

        dao.deleteAllEntries()
        dao.deleteAllTemplates()

        assertTrue(dao.getAllTemplatesSync().isEmpty())
        assertTrue(dao.getAllEntriesSync().isEmpty())
    }
}
