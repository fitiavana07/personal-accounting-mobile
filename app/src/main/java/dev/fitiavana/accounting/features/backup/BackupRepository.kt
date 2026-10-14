package dev.fitiavana.accounting.features.backup

import dev.fitiavana.accounting.features.balances.AccountBalanceDao
import dev.fitiavana.accounting.features.accounts.AccountDao
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCacheDao
import dev.fitiavana.accounting.features.instruments.InstrumentDao
import dev.fitiavana.accounting.features.settings.AppSettingsDao
import dev.fitiavana.accounting.features.transactions.TransactionDao
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind
import dev.fitiavana.accounting.features.templates.TemplateDao
import dev.fitiavana.accounting.features.templates.TemplateEntry
import dev.fitiavana.accounting.features.templates.TransactionTemplate
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.db.AppDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

sealed class RestoreResult {
    object Success : RestoreResult()
    data class SchemaMismatch(
        val backupVersion: Int,
        val currentVersion: Int
    ) : RestoreResult()

    data class Error(val message: String) : RestoreResult()
}

/**
 * Exports/restores the entire database as a single JSON document. Restore is a full
 * replace (wipe then insert), not a merge, since FK constraints here use RESTRICT and a
 * row-level merge could leave references pointing at data absent from the backup.
 * Row shapes live in BackupRowJson.kt, shared with the P2P sync payload.
 */
class BackupRepository(
    private val database: AppDatabase,
    private val accountDao: AccountDao,
    private val instrumentDao: InstrumentDao,
    private val transactionDao: TransactionDao,
    private val balanceDao: AccountBalanceDao,
    private val exchangeRateCacheDao: ExchangeRateCacheDao,
    private val appSettingsDao: AppSettingsDao,
    private val templateDao: TemplateDao,
    private val tracker: ChangeTracker
) {

    fun export(): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA_VERSION, AppDatabase.SCHEMA_VERSION)
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis())
        root.put(
            KEY_INSTRUMENTS,
            instrumentDao.getAllSync().toJsonArray { it.toJson() })
        root.put(
            KEY_ACCOUNTS,
            accountDao.getAllSync().toJsonArray { it.toJson() })
        root.put(
            KEY_TRANSACTIONS,
            transactionDao.getAllTransactionsSync()
                .toJsonArray { it.toJson() })
        root.put(
            KEY_TRANSACTION_ENTRIES,
            transactionDao.getAllEntriesSync().toJsonArray { it.toJson() })
        root.put(
            KEY_ACCOUNT_BALANCES,
            balanceDao.getAllSync().toJsonArray { it.toJson() })
        root.put(
            KEY_EXCHANGE_RATE_CACHE,
            exchangeRateCacheDao.getAllSync().toJsonArray { it.toJson() })
        root.put(
            KEY_TEMPLATES,
            templateDao.getAllTemplatesSync().toJsonArray { it.toJson() })
        root.put(
            KEY_TEMPLATE_ENTRIES,
            templateDao.getAllEntriesSync().toJsonArray { it.toJson() })
        val settings = appSettingsDao.getSync()
        if (settings != null) {
            root.put(KEY_APP_SETTINGS, settings.toJson())
        }
        return root.toString(2)
    }

    fun restore(json: String): RestoreResult {
        val root: JSONObject
        try {
            root = JSONObject(json)
        } catch (e: JSONException) {
            return RestoreResult.Error("Backup file is not valid JSON: ${e.message}")
        }

        val backupVersion: Int
        try {
            backupVersion = root.getInt(KEY_SCHEMA_VERSION)
        } catch (e: JSONException) {
            return RestoreResult.Error("Backup file is missing a schema version")
        }
        if (backupVersion != AppDatabase.SCHEMA_VERSION) {
            return RestoreResult.SchemaMismatch(
                backupVersion,
                AppDatabase.SCHEMA_VERSION
            )
        }

        val instruments: List<Instrument>
        val accounts: List<Account>
        val transactions: List<Transaction>
        val entries: List<TransactionEntry>
        val balances: List<AccountBalance>
        val rates: List<ExchangeRateCache>
        val settings: AppSettings?
        val templates: List<TransactionTemplate>
        val templateEntries: List<TemplateEntry>
        try {
            instruments = root.getJSONArray(KEY_INSTRUMENTS)
                .toList { instrumentFromJson(it) }
            accounts =
                root.getJSONArray(KEY_ACCOUNTS).toList { accountFromJson(it) }
            transactions = root.getJSONArray(KEY_TRANSACTIONS)
                .toList { transactionFromJson(it) }
            entries = root.getJSONArray(KEY_TRANSACTION_ENTRIES)
                .toList { transactionEntryFromJson(it) }
            balances = root.getJSONArray(KEY_ACCOUNT_BALANCES)
                .toList { accountBalanceFromJson(it) }
            rates = root.getJSONArray(KEY_EXCHANGE_RATE_CACHE)
                .toList { exchangeRateCacheFromJson(it) }
            // Optional so a hand-edited backup without templates still restores.
            templates = (root.optJSONArray(KEY_TEMPLATES) ?: JSONArray())
                .toList { templateFromJson(it) }
            templateEntries = (root.optJSONArray(KEY_TEMPLATE_ENTRIES) ?: JSONArray())
                .toList { templateEntryFromJson(it) }
            settings = if (root.has(KEY_APP_SETTINGS)) {
                appSettingsFromJson(root.getJSONObject(KEY_APP_SETTINGS))
            } else {
                null
            }
        } catch (e: JSONException) {
            return RestoreResult.Error("Backup file is malformed: ${e.message}")
        }

        database.runInTransaction {
            // Delete children before parents to respect RESTRICT foreign keys.
            templateDao.deleteAllEntries()
            templateDao.deleteAllTemplates()
            transactionDao.deleteAllEntries()
            transactionDao.deleteAll()
            balanceDao.deleteAll()
            accountDao.deleteAll()
            exchangeRateCacheDao.deleteAll()
            instrumentDao.deleteAll()

            // Insert parents before children.
            instrumentDao.insertAll(instruments)
            accountDao.insertAll(accounts)
            transactionDao.insertAllTransactions(transactions)
            transactionDao.insertAllEntries(entries)
            balanceDao.insertAll(balances)
            exchangeRateCacheDao.insertAll(rates)
            templateDao.insertTemplates(templates)
            templateDao.insertEntries(templateEntries)
            if (settings != null) {
                appSettingsDao.upsert(settings)
            }

            // A restore is a bulk local write: restamp what it brought and tombstone what it replaced,
            // otherwise the next sync would bring the old data back.
            tracker.replaceAll(
                (instruments.map { RecordKey(SyncKind.INSTRUMENT, it.code) } +
                        accounts.map { RecordKey(SyncKind.ACCOUNT, it.id) } +
                        transactions.map { RecordKey(SyncKind.TRANSACTION, it.id) } +
                        templates.map { RecordKey(SyncKind.TEMPLATE, it.id) } +
                        listOfNotNull(
                            appSettingsDao.getSync()
                                ?.let { RecordKey(SyncKind.SETTINGS, AppSettings.SINGLETON_ID.toString()) }
                        )).toSet()
            )
        }

        return RestoreResult.Success
    }

    companion object {
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_INSTRUMENTS = "instruments"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_TRANSACTIONS = "transactions"
        private const val KEY_TRANSACTION_ENTRIES = "transactionEntries"
        private const val KEY_ACCOUNT_BALANCES = "accountBalances"
        private const val KEY_EXCHANGE_RATE_CACHE = "exchangeRateCache"
        private const val KEY_APP_SETTINGS = "appSettings"
        private const val KEY_TEMPLATES = "transactionTemplates"
        private const val KEY_TEMPLATE_ENTRIES = "templateEntries"
    }
}
