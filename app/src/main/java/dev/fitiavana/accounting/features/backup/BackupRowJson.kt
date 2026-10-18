package dev.fitiavana.accounting.features.backup

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.templates.TemplateEntry
import dev.fitiavana.accounting.features.templates.TransactionTemplate
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * How each table's rows are written to and read from JSON. Shared by the backup file and the P2P
 * sync payload, so the two can never disagree about a row's shape.
 */

internal inline fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray {
    val array = JSONArray()
    forEach { array.put(toJson(it)) }
    return array
}

internal inline fun <T> JSONArray.toList(fromJson: (JSONObject) -> T): List<T> =
    (0 until length()).map { fromJson(getJSONObject(it)) }

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

internal fun Instrument.toJson() = JSONObject().apply {
    put("code", code)
    put("note", note)
    put("type", type)
    put("decimalPlaces", decimalPlaces)
    if (coingeckoId != null) put("coingeckoId", coingeckoId)
    if (stockApiSymbol != null) put("stockApiSymbol", stockApiSymbol)
}

internal fun instrumentFromJson(json: JSONObject) = Instrument(
    code = json.getString("code"),
    note = json.getString("note"),
    type = json.getString("type"),
    decimalPlaces = json.optInt("decimalPlaces", 0),
    coingeckoId = json.optStringOrNull("coingeckoId"),
    stockApiSymbol = json.optStringOrNull("stockApiSymbol")
)

internal fun Account.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("type", type)
    if (instrumentCode != null) put("instrumentCode", instrumentCode)
    if (intermediaryInstrumentCode != null) put(
        "intermediaryInstrumentCode",
        intermediaryInstrumentCode
    )
    if (liquidityLevel != null) put("liquidityLevel", liquidityLevel)
    if (aprPercent != null) put("aprPercent", aprPercent)
    if (incomeOnIntermediary) put("incomeOnIntermediary", true)
    if (linkedRevenueAccountId != null) put("linkedRevenueAccountId", linkedRevenueAccountId)
}

internal fun accountFromJson(json: JSONObject) = Account(
    id = json.getString("id"),
    name = json.getString("name"),
    type = json.getString("type"),
    instrumentCode = json.optStringOrNull("instrumentCode"),
    intermediaryInstrumentCode = json.optStringOrNull("intermediaryInstrumentCode"),
    liquidityLevel = json.optStringOrNull("liquidityLevel"),
    aprPercent = if (json.has("aprPercent") && !json.isNull("aprPercent")) json.getDouble("aprPercent") else null,
    incomeOnIntermediary = json.optBoolean("incomeOnIntermediary", false),
    linkedRevenueAccountId = json.optStringOrNull("linkedRevenueAccountId")
)

internal fun Transaction.toJson() = JSONObject().apply {
    put("id", id)
    put("createdAt", createdAt)
    put("transactionDatetime", transactionDatetime)
    put("note", note)
}

internal fun transactionFromJson(json: JSONObject) = Transaction(
    id = json.getString("id"),
    createdAt = json.getLong("createdAt"),
    transactionDatetime = json.getLong("transactionDatetime"),
    note = json.getString("note")
)

internal fun TransactionTemplate.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("mode", mode)
    put("createdAt", createdAt)
}

internal fun templateFromJson(json: JSONObject) = TransactionTemplate(
    id = json.getString("id"),
    name = json.getString("name"),
    mode = json.getString("mode"),
    createdAt = json.getLong("createdAt")
)

internal fun TemplateEntry.toJson() = JSONObject().apply {
    put("id", id)
    put("templateId", templateId)
    put("accountId", accountId)
    put("slot", slot)
    put("position", position)
}

internal fun templateEntryFromJson(json: JSONObject) = TemplateEntry(
    id = json.getString("id"),
    templateId = json.getString("templateId"),
    accountId = json.getString("accountId"),
    slot = json.getString("slot"),
    position = json.getInt("position")
)

internal fun TransactionEntry.toJson() = JSONObject().apply {
    put("id", id)
    put("transactionId", transactionId)
    put("accountId", accountId)
    if (debitAmount != null) put("debitAmount", debitAmount)
    if (creditAmount != null) put("creditAmount", creditAmount)
    if (instrumentDebitAmount != null) put(
        "instrumentDebitAmount",
        instrumentDebitAmount
    )
    if (instrumentCreditAmount != null) put(
        "instrumentCreditAmount",
        instrumentCreditAmount
    )
    if (intermediaryDebitAmount != null) put(
        "intermediaryDebitAmount",
        intermediaryDebitAmount
    )
    if (intermediaryCreditAmount != null) put(
        "intermediaryCreditAmount",
        intermediaryCreditAmount
    )
}

internal fun transactionEntryFromJson(json: JSONObject) =
    TransactionEntry(
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

internal fun AccountBalance.toJson() = JSONObject().apply {
    put("accountId", accountId)
    put("balance", balance)
    put("instrumentBalance", instrumentBalance)
    put("intermediaryBalance", intermediaryBalance)
    put("updatedAt", updatedAt)
    put("createdAt", createdAt)
}

internal fun accountBalanceFromJson(json: JSONObject) = AccountBalance(
    accountId = json.getString("accountId"),
    balance = json.getLong("balance"),
    instrumentBalance = json.optLong("instrumentBalance", 0),
    intermediaryBalance = json.optLong("intermediaryBalance", 0),
    updatedAt = json.getLong("updatedAt"),
    createdAt = json.getLong("createdAt")
)

internal fun ExchangeRateCache.toJson() = JSONObject().apply {
    put("pairKey", pairKey)
    put("instrumentCode", instrumentCode)
    put("intermediaryCode", intermediaryCode)
    put("rate", rate)
    put("fetchedAt", fetchedAt)
}

internal fun exchangeRateCacheFromJson(json: JSONObject) =
    ExchangeRateCache(
        pairKey = json.getString("pairKey"),
        instrumentCode = json.getString("instrumentCode"),
        intermediaryCode = json.getString("intermediaryCode"),
        rate = json.getDouble("rate"),
        fetchedAt = json.getLong("fetchedAt")
    )

internal fun AppSettings.toJson() = JSONObject().apply {
    put("monthlyLivingExpenses", monthlyLivingExpenses)
}

internal fun appSettingsFromJson(json: JSONObject) = AppSettings(
    monthlyLivingExpenses = json.getLong("monthlyLivingExpenses")
)
