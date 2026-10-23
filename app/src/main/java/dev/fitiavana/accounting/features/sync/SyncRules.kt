package dev.fitiavana.accounting.features.sync

/**
 * The consistency rules the database enforces (unique ids, foreign keys), checked on merged and
 * repaired rows before they are written, so a bad merge is rolled back with a readable reason
 * instead of failing halfway on a constraint.
 */
object SyncRules {
    fun violation(data: SyncData): String? {
        duplicate("instrument", data.instruments.map { it.code })?.let { return it }
        duplicate("account", data.accounts.map { it.id })?.let { return it }
        duplicate("transaction", data.transactions.map { it.id })?.let { return it }
        duplicate("transaction entry", data.transactionEntries.map { it.id })?.let { return it }
        duplicate("template", data.templates.map { it.id })?.let { return it }
        duplicate("template entry", data.templateEntries.map { it.id })?.let { return it }
        duplicate("alert", data.alerts.map { it.id })?.let { return it }

        val instruments = data.instruments.map { it.code }.toSet()
        val accounts = data.accounts.map { it.id }.toSet()
        val transactions = data.transactions.map { it.id }.toSet()
        val templates = data.templates.map { it.id }.toSet()
        data.accounts.firstOrNull { it.instrumentCode != null && it.instrumentCode !in instruments }
            ?.let { return "The account ${it.id} uses an instrument that does not exist" }
        data.accounts.firstOrNull { it.intermediaryInstrumentCode != null && it.intermediaryInstrumentCode !in instruments }
            ?.let { return "The account ${it.id} uses an intermediary instrument that does not exist" }
        data.transactionEntries.firstOrNull { it.transactionId !in transactions }
            ?.let { return "The entry ${it.id} belongs to a transaction that does not exist" }
        data.transactionEntries.firstOrNull { it.accountId !in accounts }
            ?.let { return "The entry ${it.id} uses an account that does not exist" }
        data.templateEntries.firstOrNull { it.templateId !in templates }
            ?.let { return "The template entry ${it.id} belongs to a template that does not exist" }
        data.templateEntries.firstOrNull { it.accountId !in accounts }
            ?.let { return "The template entry ${it.id} uses an account that does not exist" }
        data.alerts.firstOrNull { it.accountId !in accounts }
            ?.let { return "The alert ${it.id} uses an account that does not exist" }
        return null
    }

    private fun duplicate(what: String, ids: List<String>): String? =
        ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let { "Two $what rows have the id ${it.key}" }
}
