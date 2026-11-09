package dev.fitiavana.accounting.features.sync

/**
 * What the repair changed: [modified] records now differ from what was merged, [removed] ones are
 * gone. Both must be stamped as fresh local writes, or two devices could hold different values
 * under the same stamp forever.
 */
data class RepairResult(val data: SyncData, val modified: Set<RecordKey>, val removed: Set<RecordKey>)

/**
 * Merging row by row can break rules that hold across rows: an instrument deleted on one device
 * while another gave it to an account, an account deleted on one device while another recorded a
 * transaction on it. The repair is deterministic and a function of the merged rows only, so every
 * device repairs alike, and it is idempotent. Parents come first:
 *
 *  1. an account that uses an instrument that is gone loses that reference (its data is kept: a
 *     missing reference is better than a deleted account with its transactions);
 *  2. a transaction with an entry on an account that is gone is dropped whole, entries included;
 *  3. a template entry on an account that is gone is removed (as the foreign key would locally);
 *  4. an alert on an account that is gone is removed (it is tombstoned, as the foreign key would delete it);
 *  5. a bank account transaction on a bank account that is gone is removed (tombstoned, like an alert);
 *  6. each bank account's balance and balance date are recomputed from its transactions (not a local write).
 */
object SyncRepair {
    fun repair(data: SyncData): RepairResult {
        val modified = mutableSetOf<RecordKey>()
        val removed = mutableSetOf<RecordKey>()

        val instruments = data.instruments.map { it.code }.toSet()
        val allAccountIds = data.accounts.map { it.id }.toSet()
        val accounts = data.accounts.map { account ->
            val instrument = account.instrumentCode?.takeIf { it in instruments }
            val intermediary = account.intermediaryInstrumentCode?.takeIf { it in instruments }
            val linkedRevenue = account.linkedRevenueAccountId?.takeIf { it in allAccountIds }
            if (instrument == account.instrumentCode && intermediary == account.intermediaryInstrumentCode &&
                linkedRevenue == account.linkedRevenueAccountId
            ) account
            else {
                modified += RecordKey(SyncKind.ACCOUNT, account.id)
                account.copy(
                    instrumentCode = instrument,
                    intermediaryInstrumentCode = intermediary,
                    linkedRevenueAccountId = linkedRevenue
                )
            }
        }

        val accountIds = accounts.map { it.id }.toSet()
        val badTransactions = data.transactionEntries.filter { it.accountId !in accountIds }.map { it.transactionId }.toSet()
        val transactions = data.transactions.filter { it.id !in badTransactions }
        removed += data.transactions.filter { it.id in badTransactions }.map { RecordKey(SyncKind.TRANSACTION, it.id) }
        val entries = data.transactionEntries.filter { it.transactionId !in badTransactions }

        val templateEntries = data.templateEntries.filter { entry ->
            (entry.accountId in accountIds).also { if (!it) modified += RecordKey(SyncKind.TEMPLATE, entry.templateId) }
        }

        val alerts = data.alerts.filter { it.accountId in accountIds }
        removed += data.alerts.filter { it.accountId !in accountIds }.map { RecordKey(SyncKind.ALERT, it.id) }

        val bankAccountIds = data.bankAccounts.map { it.id }.toSet()
        val bankTransactions = data.bankAccountTransactions.filter { it.accountId in bankAccountIds }
        removed += data.bankAccountTransactions.filter { it.accountId !in bankAccountIds }
            .map { RecordKey(SyncKind.BANK_ACCOUNT_TRANSACTION, it.id) }
        // The balance and its date are derived from the transactions, so every device computes the same
        // ones and they need no stamp of their own.
        val byAccount = bankTransactions.groupBy { it.accountId }
        val bankAccounts = data.bankAccounts.map { account ->
            val own = byAccount[account.id].orEmpty()
            account.copy(balance = own.sumOf { it.amount }, balanceDate = own.maxOfOrNull { it.transactionDateTime })
        }

        return RepairResult(
            data.copy(
                accounts = accounts,
                transactions = transactions,
                transactionEntries = entries,
                templateEntries = templateEntries,
                alerts = alerts,
                bankAccounts = bankAccounts,
                bankAccountTransactions = bankTransactions,
            ),
            modified,
            removed,
        )
    }
}
