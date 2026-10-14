package dev.fitiavana.accounting.features.templates

import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind
import java.util.UUID

/** Saves, lists and deletes transaction templates. All calls are synchronous: use them off the main thread. */
class TemplateRepository(
    private val dao: TemplateDao,
    private val tracker: ChangeTracker,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {

    /** Stores a template of [mode] whose entries are [slots] in the given order. */
    fun save(name: String, mode: String, slots: List<TemplateSlot>) = tracker.inTransaction {
        val template = TransactionTemplate(
            id = idGenerator(),
            name = name.trim(),
            mode = mode,
            createdAt = clock()
        )
        dao.insertTemplate(template)
        dao.insertEntries(
            slots.mapIndexed { index, slot ->
                TemplateEntry(
                    id = idGenerator(),
                    templateId = template.id,
                    accountId = slot.accountId,
                    slot = slot.slot,
                    position = index
                )
            }
        )
        tracker.touch(SyncKind.TEMPLATE, template.id)
    }

    /** Newest first; templates that lost an account to deletion are left out. */
    fun getAll(): List<TemplateWithEntries> =
        dao.getAllWithEntriesSync()
            .map { it.copy(entries = it.entries.sortedBy { entry -> entry.position }) }
            .filter { it.isComplete() }

    fun get(id: String): TemplateWithEntries? = dao.getWithEntriesSync(id)

    fun delete(id: String) = tracker.inTransaction {
        tracker.deleted(listOf(RecordKey(SyncKind.TEMPLATE, id)))
        dao.deleteTemplate(id)
    }
}
