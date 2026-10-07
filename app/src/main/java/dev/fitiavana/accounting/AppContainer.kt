package dev.fitiavana.accounting

import android.content.Context
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.backup.AlarmManagerBackupScheduler
import dev.fitiavana.accounting.features.backup.AutoBackupManager
import dev.fitiavana.accounting.features.backup.BackupRepository
import dev.fitiavana.accounting.features.backup.SharedPreferencesBackupPrefsStore
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.cexprices.CexPriceRepository
import dev.fitiavana.accounting.features.cexprices.SharedPreferencesCexPairStore
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.p2pprices.P2pPriceRepository
import dev.fitiavana.accounting.features.p2pprices.SharedPreferencesP2pFilterStore
import dev.fitiavana.accounting.features.settings.AppSettingsRepository
import dev.fitiavana.accounting.features.templates.TemplateRepository
import dev.fitiavana.accounting.features.transactions.TransactionRepository
import java.io.File
import dev.fitiavana.accounting.network.cex.HttpCexPriceFetcher
import dev.fitiavana.accounting.network.p2p.HttpP2pPriceFetcher

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
    val cexPriceRepository = CexPriceRepository(
        HttpCexPriceFetcher.createAll(),
        SharedPreferencesCexPairStore(
            context.getSharedPreferences(SharedPreferencesCexPairStore.PREFS_NAME, Context.MODE_PRIVATE)
        )
    )
    val p2pPriceRepository = P2pPriceRepository(
        HttpP2pPriceFetcher(),
        SharedPreferencesP2pFilterStore(
            context.getSharedPreferences(SharedPreferencesP2pFilterStore.PREFS_NAME, Context.MODE_PRIVATE)
        )
    )
    val settingsRepository = AppSettingsRepository(database.appSettingsDao())
    val backupRepository = BackupRepository(
        database,
        database.accountDao(),
        database.instrumentDao(),
        database.transactionDao(),
        database.accountBalanceDao(),
        database.exchangeRateCacheDao(),
        database.appSettingsDao(),
        database.templateDao()
    )
    val templateRepository = TemplateRepository(database.templateDao())

    val autoBackupManager = AutoBackupManager(
        export = backupRepository::export,
        backupDir = {
            // App-specific external dir needs no permission; fall back to internal storage if unmounted.
            context.getExternalFilesDir(AUTO_BACKUP_DIR) ?: File(context.filesDir, AUTO_BACKUP_DIR)
        },
        store = SharedPreferencesBackupPrefsStore(
            context.getSharedPreferences(SharedPreferencesBackupPrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
        ),
        scheduler = AlarmManagerBackupScheduler(context)
    )

    companion object {
        private const val AUTO_BACKUP_DIR = "backups"

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
