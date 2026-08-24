package dev.fitiavana.accounting.features.backup

import dev.fitiavana.accounting.features.balances.AccountBalanceDao
import dev.fitiavana.accounting.features.accounts.AccountDao
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCacheDao
import dev.fitiavana.accounting.features.instruments.InstrumentDao
import dev.fitiavana.accounting.features.transactions.TransactionDao
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.db.AppDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

sealed class RestoreResult {
    object Success : RestoreResult()
    data class SchemaMismatch(val backupVersion: Int, val currentVersion: Int) : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}

/**
 * Exports/restores the entire database as a single JSON document. Restore is a full
 * replace (wipe then insert), not a merge, since FK constraints here use RESTRICT and a
 * row-level merge could leave references pointing at data absent from the backup.
 */
class BackupRepository(
    private val database: AppDatabase,
    private val accountDao: AccountDao,
    private val instrumentDao: InstrumentDao,
    private val transactionDao: TransactionDao,
    private val balanceDao: AccountBalanceDao,
    private val exchangeRateCacheDao: ExchangeRateCacheDao
) {

    fun export(): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA_VERSION, AppDatabase.SCHEMA_VERSION)
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis())
        root.put(KEY_INSTRUMENTS, instrumentDao.getAllSync().toJsonArray { it.toJson() })
        root.put(KEY_ACCOUNTS, accountDao.getAllSync().toJsonArray { it.toJson() })
        root.put(KEY_TRANSACTIONS, transactionDao.getAllTransactionsSync().toJsonArray { it.toJson() })
        root.put(KEY_TRANSACTION_ENTRIES, transactionDao.getAllEntriesSync().toJsonArray { it.toJson() })
        root.put(KEY_ACCOUNT_BALANCES, balanceDao.getAllSync().toJsonArray { it.toJson() })
        root.put(KEY_EXCHANGE_RATE_CACHE, exchangeRateCacheDao.getAllSync().toJsonArray { it.toJson() })
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
            return RestoreResult.SchemaMismatch(backupVersion, AppDatabase.SCHEMA_VERSION)
        }

        val instruments: List<Instrument>
        val accounts: List<Account>
        val transactions: List<Transaction>
        val entries: List<TransactionEntry>
        val balances: List<AccountBalance>
        val rates: List<ExchangeRateCache>
        try {
            instruments = root.getJSONArray(KEY_INSTRUMENTS).toList { instrumentFromJson(it) }
            accounts = root.getJSONArray(KEY_ACCOUNTS).toList { accountFromJson(it) }
            transactions = root.getJSONArray(KEY_TRANSACTIONS).toList { transactionFromJson(it) }
            entries = root.getJSONArray(KEY_TRANSACTION_ENTRIES).toList { transactionEntryFromJson(it) }
            balances = root.getJSONArray(KEY_ACCOUNT_BALANCES).toList { accountBalanceFromJson(it) }
            rates = root.getJSONArray(KEY_EXCHANGE_RATE_CACHE).toList { exchangeRateCacheFromJson(it) }
        } catch (e: JSONException) {
            return RestoreResult.Error("Backup file is malformed: ${e.message}")
        }

        database.runInTransaction {
            // Delete children before parents to respect RESTRICT foreign keys.
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

        private inline fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray {
            val array = JSONArray()
            forEach { array.put(toJson(it)) }
            return array
        }

        private inline fun <T> JSONArray.toList(fromJson: (JSONObject) -> T): List<T> =
            (0 until length()).map { fromJson(getJSONObject(it)) }

        private fun JSONObject.optLongOrNull(key: String): Long? =
            if (has(key) && !isNull(key)) getLong(key) else null

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (has(key) && !isNull(key)) getString(key) else null

        private fun Instrument.toJson() = JSONObject().apply {
            put("code", code)
            put("note", note)
            put("type", type)
            put("decimalPlaces", decimalPlaces)
            if (coingeckoId != null) put("coingeckoId", coingeckoId)
            if (stockApiSymbol != null) put("stockApiSymbol", stockApiSymbol)
        }

        private fun instrumentFromJson(json: JSONObject) = Instrument(
            code = json.getString("code"),
            note = json.getString("note"),
            type = json.getString("type"),
            decimalPlaces = json.optInt("decimalPlaces", 0),
            coingeckoId = json.optStringOrNull("coingeckoId"),
            stockApiSymbol = json.optStringOrNull("stockApiSymbol")
        )

        private fun Account.toJson() = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("type", type)
            if (instrumentCode != null) put("instrumentCode", instrumentCode)
            if (intermediaryInstrumentCode != null) put("intermediaryInstrumentCode", intermediaryInstrumentCode)
        }

        private fun accountFromJson(json: JSONObject) = Account(
            id = json.getString("id"),
            name = json.getString("name"),
            type = json.getString("type"),
            instrumentCode = json.optStringOrNull("instrumentCode"),
            intermediaryInstrumentCode = json.optStringOrNull("intermediaryInstrumentCode")
        )

        private fun Transaction.toJson() = JSONObject().apply {
            put("id", id)
            put("createdAt", createdAt)
            put("transactionDatetime", transactionDatetime)
            put("note", note)
        }

        private fun transactionFromJson(json: JSONObject) = Transaction(
            id = json.getString("id"),
            createdAt = json.getLong("createdAt"),
            transactionDatetime = json.getLong("transactionDatetime"),
            note = json.getString("note")
        )

        private fun TransactionEntry.toJson() = JSONObject().apply {
            put("id", id)
            put("transactionId", transactionId)
            put("accountId", accountId)
            if (debitAmount != null) put("debitAmount", debitAmount)
            if (creditAmount != null) put("creditAmount", creditAmount)
            if (instrumentDebitAmount != null) put("instrumentDebitAmount", instrumentDebitAmount)
            if (instrumentCreditAmount != null) put("instrumentCreditAmount", instrumentCreditAmount)
            if (intermediaryDebitAmount != null) put("intermediaryDebitAmount", intermediaryDebitAmount)
            if (intermediaryCreditAmount != null) put("intermediaryCreditAmount", intermediaryCreditAmount)
        }

        private fun transactionEntryFromJson(json: JSONObject) = TransactionEntry(
            id = json.getString("id"),
            transactionId = json.getString("transactionId"),
            accountId = json.getString("accountId"),
            debitAmount = json.optLongOrNull("debitAmount"),
            creditAmount = json.optLongOrNull("creditAmount"),
            instrumentDebitAmount = json.optLongOrNull("instrumentDebitAmount"),
            instrumentCreditAmount = json.optLongOrNull("instrumentCreditAmount"),
            intermediaryDebitAmount = json.optLongOrNull("intermediaryDebitAmount"),
            intermediaryCreditAmount = json.optLongOrNull("intermediaryCreditAmount")
        )

        private fun AccountBalance.toJson() = JSONObject().apply {
            put("accountId", accountId)
            put("balance", balance)
            put("instrumentBalance", instrumentBalance)
            put("intermediaryBalance", intermediaryBalance)
            put("updatedAt", updatedAt)
            put("createdAt", createdAt)
        }

        private fun accountBalanceFromJson(json: JSONObject) = AccountBalance(
            accountId = json.getString("accountId"),
            balance = json.getLong("balance"),
            instrumentBalance = json.optLong("instrumentBalance", 0),
            intermediaryBalance = json.optLong("intermediaryBalance", 0),
            updatedAt = json.getLong("updatedAt"),
            createdAt = json.getLong("createdAt")
        )

        private fun ExchangeRateCache.toJson() = JSONObject().apply {
            put("pairKey", pairKey)
            put("instrumentCode", instrumentCode)
            put("intermediaryCode", intermediaryCode)
            put("rate", rate)
            put("fetchedAt", fetchedAt)
        }

        private fun exchangeRateCacheFromJson(json: JSONObject) = ExchangeRateCache(
            pairKey = json.getString("pairKey"),
            instrumentCode = json.getString("instrumentCode"),
            intermediaryCode = json.getString("intermediaryCode"),
            rate = json.getDouble("rate"),
            fetchedAt = json.getLong("fetchedAt")
        )
    }
}
