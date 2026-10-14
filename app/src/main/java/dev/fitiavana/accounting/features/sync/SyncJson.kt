package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.FormatException
import dev.fitiavana.accounting.features.array
import dev.fitiavana.accounting.features.backup.accountFromJson
import dev.fitiavana.accounting.features.backup.appSettingsFromJson
import dev.fitiavana.accounting.features.backup.instrumentFromJson
import dev.fitiavana.accounting.features.backup.templateEntryFromJson
import dev.fitiavana.accounting.features.backup.templateFromJson
import dev.fitiavana.accounting.features.backup.toJson
import dev.fitiavana.accounting.features.backup.transactionEntryFromJson
import dev.fitiavana.accounting.features.backup.transactionFromJson
import dev.fitiavana.accounting.features.boolean
import dev.fitiavana.accounting.features.int
import dev.fitiavana.accounting.features.long
import dev.fitiavana.accounting.features.obj
import dev.fitiavana.accounting.features.rows
import dev.fitiavana.accounting.features.string
import org.json.JSONException
import org.json.JSONObject

/**
 * A [SyncSnapshot] as JSON: the rows in the same shape as a backup (BackupRowJson.kt) plus the
 * `meta` of every record. Decoding is strict, so a foreign or hand-edited document is rejected.
 */
object SyncJson {
    fun encode(snapshot: SyncSnapshot): JSONObject {
        val data = snapshot.data
        return JSONObject()
            .put("schemaVersion", data.schemaVersion)
            .put("instruments", array(data.instruments) { it.toJson() })
            .put("accounts", array(data.accounts) { it.toJson() })
            .put("transactions", array(data.transactions) { it.toJson() })
            .put("transactionEntries", array(data.transactionEntries) { it.toJson() })
            .put("templates", array(data.templates) { it.toJson() })
            .put("templateEntries", array(data.templateEntries) { it.toJson() })
            .apply { data.settings?.let { put("settings", it.toJson()) } }
            .put("meta", array(snapshot.meta.entries) { (key, meta) ->
                put("kind", key.kind.name)
                put("id", key.id)
                put("time", meta.stamp.time)
                put("device", meta.stamp.deviceId)
                put("deleted", meta.deleted)
            })
    }

    /** Throws [FormatException] for anything that is not a well-formed snapshot. */
    fun decode(root: JSONObject): SyncSnapshot = try {
        val data = SyncData(
            schemaVersion = root.int("schemaVersion"),
            instruments = root.rows("instruments") { instrumentFromJson(this) },
            accounts = root.rows("accounts") { accountFromJson(this) },
            transactions = root.rows("transactions") { transactionFromJson(this) },
            transactionEntries = root.rows("transactionEntries") { transactionEntryFromJson(this) },
            templates = root.rows("templates") { templateFromJson(this) },
            templateEntries = root.rows("templateEntries") { templateEntryFromJson(this) },
            settings = if (root.has("settings")) appSettingsFromJson(root.obj("settings")) else null,
        )
        val meta = root.rows("meta") {
            val kind = string("kind")
            RecordKey(
                SyncKind.values().firstOrNull { it.name == kind } ?: throw FormatException("Unknown kind '$kind'"),
                string("id"),
            ) to Meta(Stamp(long("time"), string("device")), boolean("deleted"))
        }
        SyncSnapshot(data, meta.toMap())
    } catch (e: JSONException) {
        throw FormatException(e.message ?: "Malformed snapshot")
    }
}
