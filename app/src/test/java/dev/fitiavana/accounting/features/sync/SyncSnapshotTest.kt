package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.sync.SyncFixtures.account
import dev.fitiavana.accounting.features.sync.SyncFixtures.data
import dev.fitiavana.accounting.features.sync.SyncFixtures.dead
import dev.fitiavana.accounting.features.sync.SyncFixtures.entry
import dev.fitiavana.accounting.features.sync.SyncFixtures.instrument
import dev.fitiavana.accounting.features.sync.SyncFixtures.key
import dev.fitiavana.accounting.features.sync.SyncFixtures.live
import dev.fitiavana.accounting.features.sync.SyncFixtures.stamped
import dev.fitiavana.accounting.features.sync.SyncFixtures.template
import dev.fitiavana.accounting.features.sync.SyncFixtures.templateEntry
import dev.fitiavana.accounting.features.sync.SyncFixtures.transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSnapshotTest {
    private val full = data(
        instruments = listOf(instrument("USD")),
        accounts = listOf(account("a1", "USD")),
        transactions = listOf(transaction("t1")),
        transactionEntries = listOf(entry("e1", "t1", "a1")),
        templates = listOf(template("tp1")),
        templateEntries = listOf(templateEntry("te1", "tp1", "a1")),
        settings = AppSettings(monthlyLivingExpenses = 5),
    )

    @Test
    fun `record keys list one key per record, with entries folded into their parent`() {
        assertEquals(
            setOf(
                key(SyncKind.INSTRUMENT, "USD"), key(SyncKind.ACCOUNT, "a1"), key(SyncKind.TRANSACTION, "t1"),
                key(SyncKind.TEMPLATE, "tp1"), key(SyncKind.SETTINGS, "0"),
            ),
            full.recordKeys(),
        )
    }

    @Test
    fun `a consistent snapshot has no violation`() {
        assertNull(stamped(full).violation())
        assertNull(SyncSnapshot(data(), emptyMap()).violation())
    }

    @Test
    fun `tombstones for records that have no row are fine`() {
        val snapshot = SyncSnapshot(data(), mapOf(key(SyncKind.ACCOUNT, "gone") to dead(3)))

        assertNull(snapshot.violation())
    }

    @Test
    fun `a row without a stamp is a violation`() {
        val snapshot = SyncSnapshot(full, stamped(full).meta - key(SyncKind.ACCOUNT, "a1"))

        assertNotNull(snapshot.violation())
    }

    @Test
    fun `a row whose stamp is a tombstone is a violation`() {
        val snapshot = SyncSnapshot(full, stamped(full).meta + (key(SyncKind.ACCOUNT, "a1") to dead(9)))

        assertNotNull(snapshot.violation())
    }

    @Test
    fun `a live stamp without a row is a violation`() {
        val snapshot = SyncSnapshot(full, stamped(full).meta + (key(SyncKind.INSTRUMENT, "EUR") to live(2)))

        assertNotNull(snapshot.violation())
    }

    @Test
    fun `two rows with the same id are a violation`() {
        val duplicated = full.copy(accounts = listOf(account("a1"), account("a1")))

        assertNotNull(stamped(duplicated).violation())
    }

    @Test
    fun `an entry whose transaction or template is not in the snapshot is a violation`() {
        val orphanEntry = full.copy(transactionEntries = full.transactionEntries + entry("e9", "missing", "a1"))
        val orphanTemplateEntry = full.copy(templateEntries = full.templateEntries + templateEntry("te9", "missing", "a1"))

        assertNotNull(stamped(orphanEntry).violation())
        assertNotNull(stamped(orphanTemplateEntry).violation())
    }
}
