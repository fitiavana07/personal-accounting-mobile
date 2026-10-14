package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.sync.SyncFixtures.account
import dev.fitiavana.accounting.features.sync.SyncFixtures.data
import dev.fitiavana.accounting.features.sync.SyncFixtures.entry
import dev.fitiavana.accounting.features.sync.SyncFixtures.instrument
import dev.fitiavana.accounting.features.sync.SyncFixtures.template
import dev.fitiavana.accounting.features.sync.SyncFixtures.templateEntry
import dev.fitiavana.accounting.features.sync.SyncFixtures.transaction
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SyncRulesTest {
    private val valid = data(
        instruments = listOf(instrument("USD")),
        accounts = listOf(account("a1", "USD")),
        transactions = listOf(transaction("t1")),
        transactionEntries = listOf(entry("e1", "t1", "a1")),
        templates = listOf(template("tp")),
        templateEntries = listOf(templateEntry("te", "tp", "a1")),
    )

    @Test
    fun `valid data has no violation`() {
        assertNull(SyncRules.violation(valid))
        assertNull(SyncRules.violation(data()))
    }

    @Test
    fun `a dangling instrument reference is a violation`() {
        assertNotNull(SyncRules.violation(valid.copy(accounts = listOf(account("a1", "EUR")))))
        assertNotNull(SyncRules.violation(valid.copy(accounts = listOf(account("a1", "USD", intermediary = "EUR")))))
    }

    @Test
    fun `a dangling account reference is a violation`() {
        assertNotNull(SyncRules.violation(valid.copy(transactionEntries = listOf(entry("e1", "t1", "gone")))))
        assertNotNull(SyncRules.violation(valid.copy(templateEntries = listOf(templateEntry("te", "tp", "gone")))))
    }

    @Test
    fun `an entry of a missing transaction or template is a violation`() {
        assertNotNull(SyncRules.violation(valid.copy(transactionEntries = listOf(entry("e1", "nope", "a1")))))
        assertNotNull(SyncRules.violation(valid.copy(templateEntries = listOf(templateEntry("te", "nope", "a1")))))
    }

    @Test
    fun `duplicate ids are a violation`() {
        assertNotNull(SyncRules.violation(valid.copy(instruments = listOf(instrument("USD"), instrument("USD")))))
        assertNotNull(SyncRules.violation(valid.copy(accounts = listOf(account("a1"), account("a1")))))
        assertNotNull(SyncRules.violation(valid.copy(transactions = listOf(transaction("t1"), transaction("t1")))))
        assertNotNull(
            SyncRules.violation(valid.copy(transactionEntries = listOf(entry("e1", "t1", "a1"), entry("e1", "t1", "a1"))))
        )
        assertNotNull(SyncRules.violation(valid.copy(templates = listOf(template("tp"), template("tp")))))
        assertNotNull(
            SyncRules.violation(valid.copy(templateEntries = listOf(templateEntry("te", "tp", "a1"), templateEntry("te", "tp", "a1"))))
        )
    }
}
