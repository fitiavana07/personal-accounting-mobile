package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.sync.SyncFixtures.account
import dev.fitiavana.accounting.features.sync.SyncFixtures.data
import dev.fitiavana.accounting.features.sync.SyncFixtures.entry
import dev.fitiavana.accounting.features.sync.SyncFixtures.instrument
import dev.fitiavana.accounting.features.sync.SyncFixtures.key
import dev.fitiavana.accounting.features.sync.SyncFixtures.template
import dev.fitiavana.accounting.features.sync.SyncFixtures.templateEntry
import dev.fitiavana.accounting.features.sync.SyncFixtures.transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepairTest {
    @Test
    fun `consistent data is left alone`() {
        val data = data(
            instruments = listOf(instrument("USD")),
            accounts = listOf(account("a1", "USD")),
            transactions = listOf(transaction("t1")),
            transactionEntries = listOf(entry("e1", "t1", "a1")),
            templates = listOf(template("tp")),
            templateEntries = listOf(templateEntry("te", "tp", "a1")),
        )

        val result = SyncRepair.repair(data)

        assertEquals(data, result.data)
        assertTrue(result.modified.isEmpty())
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun `an account whose instrument is gone loses the reference but keeps its data`() {
        val data = data(
            instruments = listOf(instrument("EUR")),
            accounts = listOf(account("a1", instrumentCode = "USD", intermediary = "EUR")),
            transactions = listOf(transaction("t1")),
            transactionEntries = listOf(entry("e1", "t1", "a1")),
        )

        val result = SyncRepair.repair(data)

        assertEquals(account("a1", instrumentCode = null, intermediary = "EUR"), result.data.accounts.single())
        assertEquals(setOf(key(SyncKind.ACCOUNT, "a1")), result.modified)
        assertEquals(data.transactions, result.data.transactions)
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun `an account linked to a revenue account that is gone loses the link`() {
        val data = data(
            instruments = listOf(instrument("USD")),
            accounts = listOf(account("a1", instrumentCode = "USD", linkedRevenue = "gone")),
        )

        val result = SyncRepair.repair(data)

        assertEquals(account("a1", instrumentCode = "USD", linkedRevenue = null), result.data.accounts.single())
        assertEquals(setOf(key(SyncKind.ACCOUNT, "a1")), result.modified)
    }

    @Test
    fun `an account linked to an existing account keeps the link`() {
        val data = data(
            instruments = listOf(instrument("USD")),
            accounts = listOf(account("a1", instrumentCode = "USD", linkedRevenue = "rev"), account("rev")),
        )

        val result = SyncRepair.repair(data)

        assertEquals(data.accounts, result.data.accounts)
        assertTrue(result.modified.isEmpty())
    }

    @Test
    fun `an account whose intermediary instrument is gone loses that reference`() {
        val data = data(
            instruments = listOf(instrument("USD")),
            accounts = listOf(account("a1", instrumentCode = "USD", intermediary = "AR")),
        )

        val result = SyncRepair.repair(data)

        assertEquals(account("a1", instrumentCode = "USD", intermediary = null), result.data.accounts.single())
    }

    @Test
    fun `a transaction with an entry on a missing account is dropped with its entries`() {
        val data = data(
            accounts = listOf(account("a1")),
            transactions = listOf(transaction("good"), transaction("bad")),
            transactionEntries = listOf(
                entry("e1", "good", "a1"),
                entry("e2", "bad", "a1"),
                entry("e3", "bad", "gone"),
            ),
        )

        val result = SyncRepair.repair(data)

        assertEquals(listOf(transaction("good")), result.data.transactions)
        assertEquals(listOf(entry("e1", "good", "a1")), result.data.transactionEntries)
        assertEquals(setOf(key(SyncKind.TRANSACTION, "bad")), result.removed)
        assertTrue(result.modified.isEmpty())
    }

    @Test
    fun `a template entry on a missing account is removed and the template counts as changed`() {
        val data = data(
            accounts = listOf(account("a1")),
            templates = listOf(template("tp"), template("other")),
            templateEntries = listOf(
                templateEntry("te1", "tp", "a1"),
                templateEntry("te2", "tp", "gone", position = 1),
                templateEntry("te3", "other", "a1"),
            ),
        )

        val result = SyncRepair.repair(data)

        assertEquals(listOf("te1", "te3"), result.data.templateEntries.map { it.id })
        assertEquals(setOf(key(SyncKind.TEMPLATE, "tp")), result.modified)
        assertEquals(data.templates, result.data.templates)
    }

    @Test
    fun `repairing is idempotent and its result satisfies the rules`() {
        val broken = data(
            accounts = listOf(account("a1", instrumentCode = "gone")),
            transactions = listOf(transaction("bad")),
            transactionEntries = listOf(entry("e", "bad", "missing")),
            templates = listOf(template("tp")),
            templateEntries = listOf(templateEntry("te", "tp", "missing")),
        )

        val once = SyncRepair.repair(broken)
        val twice = SyncRepair.repair(once.data)

        assertEquals(once.data, twice.data)
        assertTrue(twice.modified.isEmpty())
        assertTrue(twice.removed.isEmpty())
        assertNull(SyncRules.violation(once.data))
    }
}
