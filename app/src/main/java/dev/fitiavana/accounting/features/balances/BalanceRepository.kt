package dev.fitiavana.accounting.features.balances

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.accounts.AccountDao
import dev.fitiavana.accounting.features.transactions.TransactionDao

class BalanceRepository(
    private val accountDao: AccountDao,
    private val balanceDao: AccountBalanceDao,
    private val transactionDao: TransactionDao
) {
    fun getAll(): LiveData<List<AccountBalance>> = balanceDao.getAll()

    fun getAllSync(): List<AccountBalance> = balanceDao.getAllSync()

    fun getByAccountId(accountId: String): AccountBalance? =
        balanceDao.getByAccountId(accountId)

    fun recalculateForAccount(accountId: String, accountType: String) {
        val totalDebits = transactionDao.sumDebitsForAccount(accountId)
        val totalCredits = transactionDao.sumCreditsForAccount(accountId)
        val balance =
            BalanceCalculator.compute(accountType, totalDebits, totalCredits)

        val totalInstrumentDebits =
            transactionDao.sumInstrumentDebitsForAccount(accountId)
        val totalInstrumentCredits =
            transactionDao.sumInstrumentCreditsForAccount(accountId)
        val instrumentBalance = BalanceCalculator.compute(
            accountType,
            totalInstrumentDebits,
            totalInstrumentCredits
        )

        val totalIntermediaryDebits =
            transactionDao.sumIntermediaryDebitsForAccount(accountId)
        val totalIntermediaryCredits =
            transactionDao.sumIntermediaryCreditsForAccount(accountId)
        val intermediaryBalance = BalanceCalculator.compute(
            accountType,
            totalIntermediaryDebits,
            totalIntermediaryCredits
        )

        val now = System.currentTimeMillis()
        val existing = balanceDao.getByAccountId(accountId)
        balanceDao.insert(
            AccountBalance(
                accountId = accountId,
                balance = balance,
                instrumentBalance = instrumentBalance,
                intermediaryBalance = intermediaryBalance,
                updatedAt = now,
                createdAt = existing?.createdAt ?: now
            )
        )
    }

    fun recalculateAll() {
        val accounts = accountDao.getAllSync()
        for (account in accounts) {
            recalculateForAccount(account.id, account.type)
        }
    }

    fun hasTransactions(accountId: String): Boolean {
        return transactionDao.countEntriesForAccount(accountId) > 0
    }

    fun computeBalancesAsOf(asOfMs: Long): Map<String, Long> {
        val accounts = accountDao.getAllSync()
        return accounts.associate { account ->
            val debits =
                transactionDao.sumDebitsForAccountUpTo(account.id, asOfMs)
            val credits =
                transactionDao.sumCreditsForAccountUpTo(account.id, asOfMs)
            account.id to BalanceCalculator.compute(
                account.type,
                debits,
                credits
            )
        }
    }

    fun computeBalancesBetween(startMs: Long, endMs: Long): Map<String, Long> {
        val accounts = accountDao.getAllSync()
        return accounts.associate { account ->
            val debits = transactionDao.sumDebitsForAccountBetween(
                account.id,
                startMs,
                endMs
            )
            val credits = transactionDao.sumCreditsForAccountBetween(
                account.id,
                startMs,
                endMs
            )
            account.id to BalanceCalculator.compute(
                account.type,
                debits,
                credits
            )
        }
    }

    fun getTransactionDateRange(): Pair<Long, Long>? {
        val min = transactionDao.getMinTransactionDatetime() ?: return null
        val max = transactionDao.getMaxTransactionDatetime() ?: return null
        return min to max
    }
}
