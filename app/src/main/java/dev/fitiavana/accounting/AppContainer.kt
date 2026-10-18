package dev.fitiavana.accounting

import android.content.Context
import androidx.annotation.VisibleForTesting
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.backup.AlarmManagerBackupScheduler
import dev.fitiavana.accounting.features.backup.AutoBackupManager
import dev.fitiavana.accounting.features.backup.BackupRepository
import dev.fitiavana.accounting.features.backup.SharedPreferencesBackupPrefsStore
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.changelog.AssetChangelogSource
import dev.fitiavana.accounting.features.changelog.ChangelogRepository
import dev.fitiavana.accounting.features.cexprices.CexPriceRepository
import dev.fitiavana.accounting.features.cexprices.SharedPreferencesCexPairStore
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.p2pprices.P2pPriceRepository
import dev.fitiavana.accounting.features.p2pprices.SharedPreferencesP2pFilterStore
import dev.fitiavana.accounting.features.settings.AppSettingsRepository
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.SyncCoordinator
import dev.fitiavana.accounting.features.sync.SyncIdentity
import dev.fitiavana.accounting.features.sync.SyncRepository
import dev.fitiavana.accounting.features.sync.WifiDiscoveryChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dev.fitiavana.accounting.features.sync.SyncSettingsStore
import dev.fitiavana.accounting.features.templates.TemplateRepository
import dev.fitiavana.accounting.features.transactions.TransactionRepository
import java.io.File
import dev.fitiavana.accounting.network.Api19HttpClients
import dev.fitiavana.accounting.network.CoinGeckoClient
import dev.fitiavana.accounting.network.YahooFinanceClient
import dev.fitiavana.accounting.network.cex.HttpCexPriceFetcher
import dev.fitiavana.accounting.network.p2p.HttpP2pPriceFetcher
import okhttp3.OkHttpClient

/**
 * Builds each repository once from the shared [AppDatabase] instance. Activities and
 * Fragments pull repositories from here instead of constructing them from DAOs inline.
 */
class AppContainer private constructor(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val syncSettingsStore = SyncSettingsStore(
        context.getSharedPreferences(SyncSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
    )

    /** Versions every local write for P2P sync; the device id is read once, as the first write needs it. */
    val changeTracker = ChangeTracker.forDatabase(database, syncSettingsStore.deviceId())

    val accountRepository = AccountRepository(database.accountDao(), changeTracker)
    val instrumentRepository =
        InstrumentRepository(database.instrumentDao(), database.accountDao(), changeTracker)
    val transactionRepository =
        TransactionRepository(database.transactionDao(), changeTracker)
    val balanceRepository = BalanceRepository(
        database.accountDao(),
        database.accountBalanceDao(),
        database.transactionDao()
    )
    private val httpClient = httpClientFactory()

    val exchangeRateRepository = ExchangeRateRepository(
        database.exchangeRateCacheDao(),
        CoinGeckoClient(httpClient),
        YahooFinanceClient(httpClient)
    )
    val cexPriceRepository = CexPriceRepository(
        HttpCexPriceFetcher.createAll(httpClient),
        SharedPreferencesCexPairStore(
            context.getSharedPreferences(SharedPreferencesCexPairStore.PREFS_NAME, Context.MODE_PRIVATE)
        )
    )
    val p2pPriceRepository = P2pPriceRepository(
        HttpP2pPriceFetcher(client = httpClient),
        SharedPreferencesP2pFilterStore(
            context.getSharedPreferences(SharedPreferencesP2pFilterStore.PREFS_NAME, Context.MODE_PRIVATE)
        )
    )
    val changelogRepository = ChangelogRepository(AssetChangelogSource(context.assets))
    val settingsRepository = AppSettingsRepository(database.appSettingsDao(), changeTracker)
    val backupRepository = BackupRepository(
        database,
        database.accountDao(),
        database.instrumentDao(),
        database.transactionDao(),
        database.accountBalanceDao(),
        database.exchangeRateCacheDao(),
        database.appSettingsDao(),
        database.templateDao(),
        changeTracker
    )
    val templateRepository = TemplateRepository(database.templateDao(), changeTracker)

    val syncRepository = SyncRepository(
        tracker = changeTracker,
        metaDao = database.syncMetaDao(),
        syncDao = database.syncDao(),
        instrumentDao = database.instrumentDao(),
        accountDao = database.accountDao(),
        transactionDao = database.transactionDao(),
        templateDao = database.templateDao(),
        appSettingsDao = database.appSettingsDao(),
        schemaVersion = AppDatabase.SCHEMA_VERSION,
        recalculateBalances = balanceRepository::recalculateAll
    )

    private val syncIdentity = SyncIdentity.forBuild()

    /** Finds the user's other devices on the Wi-Fi and keeps the ledger in step, while the app is on screen. */
    val syncCoordinator = SyncCoordinator(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        repository = syncRepository,
        settings = syncSettingsStore,
        changes = changeTracker.changes,
        identity = syncIdentity,
        newChannel = WifiDiscoveryChannels(context, syncIdentity)::open
    )

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

        /** Builds the client every price/rate fetcher shares; unit tests swap in an offline one before the container exists. */
        @Volatile
        @VisibleForTesting
        var httpClientFactory: () -> OkHttpClient = Api19HttpClients::build

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
