package dev.fitiavana.accounting

import android.content.Context
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.backup.BackupRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.settings.AppSettingsRepository
import dev.fitiavana.accounting.features.transactions.TransactionRepository

/**
 * Builds each repository once from the shared [AppDatabase] instance. Activities and
 * Fragments pull repositories from here instead of constructing them from DAOs inline.
 */
class AppContainer private constructor(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val accountRepository = AccountRepository(database.accountDao())
    val instrumentRepository =
        InstrumentRepository(database.instrumentDao(), database.accountDao())
    val transactionRepository =
        TransactionRepository(database.transactionDao())
    val balanceRepository = BalanceRepository(
        database.accountDao(),
        database.accountBalanceDao(),
        database.transactionDao()
    )
    val exchangeRateRepository =
        ExchangeRateRepository(database.exchangeRateCacheDao())
    val settingsRepository = AppSettingsRepository(database.appSettingsDao())
    val backupRepository = BackupRepository(
        database,
        database.accountDao(),
        database.instrumentDao(),
        database.transactionDao(),
        database.accountBalanceDao(),
        database.exchangeRateCacheDao()
    )

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
