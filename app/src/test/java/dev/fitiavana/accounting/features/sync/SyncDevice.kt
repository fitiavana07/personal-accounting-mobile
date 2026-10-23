package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.alerts.AlertRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.settings.AppSettingsRepository
import dev.fitiavana.accounting.features.templates.TemplateRepository
import dev.fitiavana.accounting.features.transactions.TransactionRepository

/** One simulated phone: its own in-memory database, clock and repositories, for sync tests. */
class SyncDevice(val id: String, var now: Long = 1_000L, failOnRecalculate: Boolean = false) {
    val db: AppDatabase = inMemoryDatabase()
    val tracker = ChangeTracker.forDatabase(db, id) { now }
    val instruments = InstrumentRepository(db.instrumentDao(), db.accountDao(), tracker)
    val accounts = AccountRepository(db.accountDao(), tracker)
    val transactions = TransactionRepository(db.transactionDao(), tracker)
    val templates = TemplateRepository(db.templateDao(), tracker, clock = { 1L }, idGenerator = IdSequence(id)::next)
    val alerts = AlertRepository(db.alertDao(), tracker, clock = { 1L }, idGenerator = IdSequence("$id-alert")::next)
    val settings = AppSettingsRepository(db.appSettingsDao(), tracker)
    val balances = BalanceRepository(db.accountDao(), db.accountBalanceDao(), db.transactionDao())
    val sync = syncRepository(AppDatabase.SCHEMA_VERSION, failOnRecalculate)

    /** The sync repository of this device, optionally claiming another database version. */
    fun syncRepository(schemaVersion: Int, failOnRecalculate: Boolean = false) = SyncRepository(
        tracker = tracker,
        metaDao = db.syncMetaDao(),
        syncDao = db.syncDao(),
        instrumentDao = db.instrumentDao(),
        accountDao = db.accountDao(),
        transactionDao = db.transactionDao(),
        templateDao = db.templateDao(),
        alertDao = db.alertDao(),
        appSettingsDao = db.appSettingsDao(),
        schemaVersion = schemaVersion,
        recalculateBalances = if (failOnRecalculate) ({ error("balances failed") }) else balances::recalculateAll,
    )

    /** An instrument, two accounts and a transaction of 100 between them. */
    fun seedLedger() {
        instruments.insert(SyncFixtures.instrument("USD"))
        accounts.insert(Account("a1", "Bank a1", "asset"))
        accounts.insert(Account("a2", "Revenue a2", "revenue"))
        transactions.save(
            SyncFixtures.transaction("t1"),
            listOf(
                SyncFixtures.entry("e1", "t1", "a1", debit = 100, credit = null),
                SyncFixtures.entry("e2", "t1", "a2", debit = null, credit = 100),
            ),
        )
    }

    private class IdSequence(private val prefix: String) {
        private var n = 0
        fun next() = "$prefix-id${n++}"
    }

    val meta get() = db.syncMetaDao().all().associate { it.key() to it.meta() }

    fun stampOf(kind: SyncKind, id: String) = meta.getValue(RecordKey(kind, id)).stamp

    /** Everything that should be identical on two devices that have synced: the rows and the stamps. */
    fun state(): Pair<SyncData, Map<RecordKey, Meta>> {
        val snapshot = sync.snapshot()
        val data = snapshot.data
        return data.copy(
            instruments = data.instruments.sortedBy { it.code },
            accounts = data.accounts.sortedBy { it.id },
            transactions = data.transactions.sortedBy { it.id },
            transactionEntries = data.transactionEntries.sortedBy { it.id },
            templates = data.templates.sortedBy { it.id },
            templateEntries = data.templateEntries.sortedBy { it.id },
            alerts = data.alerts.sortedBy { it.id },
        ) to snapshot.meta
    }

    fun close() = db.close()

    companion object {
        /** A session as the protocol runs it: the responder merges the request, the initiator its answer. */
        fun sync(initiator: SyncDevice, responder: SyncDevice) {
            responder.sync.apply(initiator.sync.snapshot())
            initiator.sync.apply(responder.sync.snapshot())
        }
    }
}
