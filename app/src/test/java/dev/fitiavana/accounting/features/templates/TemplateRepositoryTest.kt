package dev.fitiavana.accounting.features.templates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TemplateRepositoryTest {

    private lateinit var dao: TemplateDao
    private lateinit var repository: TemplateRepository

    @Before
    fun setUp() {
        dao = mock()
        repository = TemplateRepository(dao, clock = { 1_000L }, idGenerator = IdSequence()::next)
    }

    private class IdSequence {
        private var n = 0
        fun next() = "id${n++}"
    }

    private fun withEntries(
        mode: String,
        vararg slotsAndAccounts: Pair<String, String>,
        id: String = "t1"
    ): TemplateWithEntries = TemplateWithEntries(
        template = TransactionTemplate(id, "Name", mode, 1L),
        entries = slotsAndAccounts.mapIndexed { index, (slot, account) ->
            TemplateEntry("e$index", id, account, slot, index)
        }
    )

    // --- save ---

    @Test
    fun `save inserts the template and one entry per slot in order`() {
        repository.save(
            name = "Withdraw",
            mode = TemplateModes.SIMPLE_TRANSFER,
            slots = listOf(TemplateSlot(TemplateSlots.FROM, "bank"), TemplateSlot(TemplateSlots.TO, "cash"))
        )

        val templateCaptor = argumentCaptor<TransactionTemplate>()
        verify(dao).insertTemplate(templateCaptor.capture())
        assertEquals("Withdraw", templateCaptor.firstValue.name)
        assertEquals(TemplateModes.SIMPLE_TRANSFER, templateCaptor.firstValue.mode)
        assertEquals(1_000L, templateCaptor.firstValue.createdAt)

        val entriesCaptor = argumentCaptor<List<TemplateEntry>>()
        verify(dao).insertEntries(entriesCaptor.capture())
        val entries = entriesCaptor.firstValue
        assertEquals(listOf(TemplateSlots.FROM, TemplateSlots.TO), entries.map { it.slot })
        assertEquals(listOf("bank", "cash"), entries.map { it.accountId })
        assertEquals(listOf(0, 1), entries.map { it.position })
        assertTrue(entries.all { it.templateId == templateCaptor.firstValue.id })
    }

    @Test
    fun `save trims the name`() {
        repository.save("  Rent  ", TemplateModes.CLASSIC, listOf(TemplateSlot(TemplateSlots.ROW, "a")))

        val captor = argumentCaptor<TransactionTemplate>()
        verify(dao).insertTemplate(captor.capture())
        assertEquals("Rent", captor.firstValue.name)
    }

    // --- getAll ---

    @Test
    fun `getAll returns complete templates with entries ordered by position`() {
        val unordered = TemplateWithEntries(
            TransactionTemplate("t1", "N", TemplateModes.SIMPLE_TRANSFER, 1L),
            listOf(
                TemplateEntry("e2", "t1", "cash", TemplateSlots.TO, 1),
                TemplateEntry("e1", "t1", "bank", TemplateSlots.FROM, 0)
            )
        )
        whenever(dao.getAllWithEntriesSync()).thenReturn(listOf(unordered))

        val result = repository.getAll()

        assertEquals(listOf("e1", "e2"), result.single().entries.map { it.id })
    }

    @Test
    fun `getAll hides templates that lost an account`() {
        val complete = withEntries(
            TemplateModes.SIMPLE_TRANSFER,
            TemplateSlots.FROM to "bank",
            TemplateSlots.TO to "cash",
            id = "ok"
        )
        val missingTo = withEntries(TemplateModes.SIMPLE_TRANSFER, TemplateSlots.FROM to "bank", id = "broken")
        whenever(dao.getAllWithEntriesSync()).thenReturn(listOf(complete, missingTo))

        assertEquals(listOf("ok"), repository.getAll().map { it.template.id })
    }

    // --- completeness per mode ---

    @Test
    fun `instrument transfer needs FROM and TO`() {
        assertTrue(
            withEntries(TemplateModes.INSTRUMENT_TRANSFER, TemplateSlots.FROM to "a", TemplateSlots.TO to "b")
                .isComplete()
        )
        assertEquals(
            false,
            withEntries(TemplateModes.INSTRUMENT_TRANSFER, TemplateSlots.TO to "b").isComplete()
        )
    }

    @Test
    fun `instrument income needs ASSET and REVENUE`() {
        assertTrue(
            withEntries(TemplateModes.INSTRUMENT_INCOME, TemplateSlots.ASSET to "a", TemplateSlots.REVENUE to "b")
                .isComplete()
        )
        assertEquals(
            false,
            withEntries(TemplateModes.INSTRUMENT_INCOME, TemplateSlots.ASSET to "a").isComplete()
        )
    }

    @Test
    fun `classic needs at least two rows`() {
        assertTrue(
            withEntries(TemplateModes.CLASSIC, TemplateSlots.ROW to "a", TemplateSlots.ROW to "b").isComplete()
        )
        assertEquals(false, withEntries(TemplateModes.CLASSIC, TemplateSlots.ROW to "a").isComplete())
    }

    @Test
    fun `an unknown mode is never complete`() {
        assertEquals(false, withEntries("weird", TemplateSlots.ROW to "a", TemplateSlots.ROW to "b").isComplete())
    }

    // --- get / delete ---

    @Test
    fun `get returns null for an unknown template`() {
        whenever(dao.getWithEntriesSync(any())).thenReturn(null)

        assertNull(repository.get("x"))
    }

    @Test
    fun `delete removes the template by id`() {
        repository.delete("t1")

        verify(dao).deleteTemplate("t1")
    }
}
