package dev.fitiavana.accounting.data.repository

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.data.dao.AccountBalanceDao
import dev.fitiavana.accounting.data.dao.AccountDao
import dev.fitiavana.accounting.data.dao.TransactionDao
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.model.BalanceCalculator

class BalanceRepository(
    private val accountDao: AccountDao,
    private val balanceDao: AccountBalanceDao,
    private val transactionDao: TransactionDao
) {
    fun getAll(): LiveData<List<AccountBalance>> = balanceDao.getAll()

    fun recalculateForAccount(accountId: String, accountType: String) {
        val totalDebits = transactionDao.sumDebitsForAccount(accountId)
        val totalCredits = transactionDao.sumCreditsForAccount(accountId)
        val balance = BalanceCalculator.compute(accountType, totalDebits, totalCredits)
        val totalInstrumentDebits = transactionDao.sumInstrumentDebitsForAccount(accountId)
        val totalInstrumentCredits = transactionDao.sumInstrumentCreditsForAccount(accountId)
        val instrumentBalance = BalanceCalculator.compute(accountType, totalInstrumentDebits, totalInstrumentCredits)
        val now = System.currentTimeMillis()
        val existing = balanceDao.getByAccountId(accountId)
        balanceDao.insert(
            AccountBalance(
                accountId = accountId,
                balance = balance,
                instrumentBalance = instrumentBalance,
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
}
