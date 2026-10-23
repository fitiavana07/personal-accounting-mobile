package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.FormatException
import dev.fitiavana.accounting.features.alerts.AlertTypes
import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.sync.SyncFixtures.account
import dev.fitiavana.accounting.features.sync.SyncFixtures.data
import dev.fitiavana.accounting.features.sync.SyncFixtures.dead
import dev.fitiavana.accounting.features.sync.SyncFixtures.entry
import dev.fitiavana.accounting.features.sync.SyncFixtures.instrument
import dev.fitiavana.accounting.features.sync.SyncFixtures.key
import dev.fitiavana.accounting.features.sync.SyncFixtures.live
import dev.fitiavana.accounting.features.sync.SyncFixtures.template
import dev.fitiavana.accounting.features.sync.SyncFixtures.templateEntry
import dev.fitiavana.accounting.features.sync.SyncFixtures.transaction
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncJsonTest {
    private val snapshot = SyncSnapshot(
        data(
            instruments = listOf(instrument("USD")),
            accounts = listOf(account("a1", "USD")),
            transactions = listOf(transaction("t1")),
            transactionEntries = listOf(entry("e1", "t1", "a1", debit = 7, credit = null)),
            templates = listOf(template("tp")),
            templateEntries = listOf(templateEntry("te", "tp", "a1")),
            settings = AppSettings(monthlyLivingExpenses = 1_500_000),
        ),
        mapOf(
            key(SyncKind.INSTRUMENT, "USD") to live(5, "dev-a"),
            key(SyncKind.ACCOUNT, "a1") to live(6, "dev-a"),
            key(SyncKind.TRANSACTION, "t1") to live(7, "dev-b"),
            key(SyncKind.TEMPLATE, "tp") to live(8, "dev-b"),
            key(SyncKind.SETTINGS, "0") to live(9, "dev-b"),
            key(SyncKind.ACCOUNT, "old") to dead(4, "dev-a"),
        ),
    )

    private fun json() = SyncJson.encode(snapshot)

    @Test
    fun `a snapshot survives a round trip through text`() {
        val decoded = SyncJson.decode(JSONObject(json().toString()))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun `a snapshot without settings round trips too`() {
        val bare = SyncSnapshot(data(), emptyMap())

        assertEquals(bare, SyncJson.decode(JSONObject(SyncJson.encode(bare).toString())))
    }

    @Test
    fun `alerts survive an encode and decode`() {
        val withAlert = snapshot.copy(
            data = snapshot.data.copy(alerts = listOf(SyncFixtures.alert("al1", "a1", AlertTypes.CROSSING_BELOW, -7.5)))
        )

        val decoded = SyncJson.decode(JSONObject(SyncJson.encode(withAlert).toString()))

        assertEquals(withAlert.data.alerts, decoded.data.alerts)
    }

    @Test
    fun `large amounts keep their exact value`() {
        val big = 9_007_199_254_740_993L
        val withBig = snapshot.copy(
            data = snapshot.data.copy(transactionEntries = listOf(entry("e1", "t1", "a1", debit = big, credit = null)))
        )

        val decoded = SyncJson.decode(JSONObject(SyncJson.encode(withBig).toString()))

        assertEquals(big, decoded.data.transactionEntries.single().debitAmount)
    }

    @Test
    fun `a missing section is rejected`() {
        listOf("schemaVersion", "instruments", "accounts", "transactions", "transactionEntries", "templates", "templateEntries", "alerts", "meta")
            .forEach { name ->
                val broken = json().apply { remove(name) }
                assertThrows(name, FormatException::class.java) { SyncJson.decode(broken) }
            }
    }

    @Test
    fun `a malformed row is rejected`() {
        val broken = json().put("accounts", JSONArray().put(JSONObject().put("id", "a1")))

        assertThrows(FormatException::class.java) { SyncJson.decode(broken) }
    }

    @Test
    fun `a malformed stamp is rejected`() {
        fun withMeta(row: JSONObject) = json().put("meta", JSONArray().put(row))
        val ok = JSONObject().put("kind", "ACCOUNT").put("id", "a1").put("time", 1).put("device", "d").put("deleted", false)

        assertThrows(FormatException::class.java) { SyncJson.decode(withMeta(JSONObject(ok.toString()).put("kind", "NOPE"))) }
        assertThrows(FormatException::class.java) { SyncJson.decode(withMeta(JSONObject(ok.toString()).put("time", "1"))) }
        assertThrows(FormatException::class.java) { SyncJson.decode(withMeta(JSONObject(ok.toString()).put("deleted", "no"))) }
        assertThrows(FormatException::class.java) { SyncJson.decode(withMeta(JSONObject(ok.toString()).apply { remove("device") })) }
    }

    @Test
    fun `a schema version that is not a number is rejected`() {
        assertThrows(FormatException::class.java) { SyncJson.decode(json().put("schemaVersion", "20")) }
    }
}
