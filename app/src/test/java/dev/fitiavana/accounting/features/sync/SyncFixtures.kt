package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.templates.TemplateEntry
import dev.fitiavana.accounting.features.templates.TemplateModes
import dev.fitiavana.accounting.features.templates.TemplateSlots
import dev.fitiavana.accounting.features.templates.TransactionTemplate
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry

/** Small builders for sync tests, so each test only names what it cares about. */
object SyncFixtures {
    fun data(
        instruments: List<Instrument> = emptyList(),
        accounts: List<Account> = emptyList(),
        transactions: List<Transaction> = emptyList(),
        transactionEntries: List<TransactionEntry> = emptyList(),
        templates: List<TransactionTemplate> = emptyList(),
        templateEntries: List<TemplateEntry> = emptyList(),
        settings: AppSettings? = null,
        schemaVersion: Int = AppDatabase.SCHEMA_VERSION,
    ) = SyncData(
        schemaVersion, instruments, accounts, transactions, transactionEntries, templates, templateEntries, settings,
    )

    fun instrument(code: String) = Instrument(code, "Note $code", "currency", 2)

    fun account(id: String, instrumentCode: String? = null, intermediary: String? = null) =
        Account(id, "Name $id", "asset", instrumentCode = instrumentCode, intermediaryInstrumentCode = intermediary)

    fun transaction(id: String) = Transaction(id, 1L, 2L, "note $id")

    fun entry(id: String, transactionId: String, accountId: String, debit: Long? = 10L, credit: Long? = null) =
        TransactionEntry(id, transactionId, accountId, debit, credit)

    fun template(id: String) = TransactionTemplate(id, "Template $id", TemplateModes.SIMPLE_TRANSFER, 5L)

    fun templateEntry(id: String, templateId: String, accountId: String, slot: String = TemplateSlots.FROM, position: Int = 0) =
        TemplateEntry(id, templateId, accountId, slot, position)

    fun live(time: Long, device: String = "a") = Meta(Stamp(time, device), deleted = false)

    fun dead(time: Long, device: String = "a") = Meta(Stamp(time, device), deleted = true)

    fun key(kind: SyncKind, id: String) = RecordKey(kind, id)

    /** A snapshot whose every row has a live meta with the given time, so only the rows matter. */
    fun stamped(data: SyncData, time: Long = 1, device: String = "a") =
        SyncSnapshot(data, data.recordKeys().associateWith { live(time, device) })
}
