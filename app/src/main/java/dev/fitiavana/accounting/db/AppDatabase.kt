package dev.fitiavana.accounting.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountDao
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.AccountBalanceDao
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCacheDao
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentDao
import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.settings.AppSettingsDao
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionDao
import dev.fitiavana.accounting.features.transactions.TransactionEntry

@Database(
    entities = [Account::class, Transaction::class, TransactionEntry::class,
        AccountBalance::class, Instrument::class, ExchangeRateCache::class, AppSettings::class],
    version = AppDatabase.SCHEMA_VERSION
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun accountBalanceDao(): AccountBalanceDao
    abstract fun instrumentDao(): InstrumentDao
    abstract fun exchangeRateCacheDao(): ExchangeRateCacheDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        const val SCHEMA_VERSION = 16

        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`creationTimestamp` INTEGER NOT NULL, " +
                            "`transactionDatetime` INTEGER NOT NULL, " +
                            "`note` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transaction_entries` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`transactionId` TEXT NOT NULL, " +
                            "`accountId` TEXT NOT NULL, " +
                            "`debitAmount` REAL, " +
                            "`creditAmount` REAL, " +
                            "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON DELETE RESTRICT)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_transactionId` ON `transaction_entries` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_accountId` ON `transaction_entries` (`accountId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transaction_entries_new` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`transactionId` TEXT NOT NULL, " +
                            "`accountId` TEXT NOT NULL, " +
                            "`debitAmount` INTEGER, " +
                            "`creditAmount` INTEGER, " +
                            "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "INSERT INTO `transaction_entries_new` SELECT `id`, `transactionId`, `accountId`, " +
                            "CAST(`debitAmount` AS INTEGER), CAST(`creditAmount` AS INTEGER) FROM `transaction_entries`"
                )
                db.execSQL("DROP TABLE `transaction_entries`")
                db.execSQL("ALTER TABLE `transaction_entries_new` RENAME TO `transaction_entries`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_transactionId` ON `transaction_entries` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_accountId` ON `transaction_entries` (`accountId`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'asset'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `account_balances` (" +
                            "`accountId` TEXT NOT NULL PRIMARY KEY, " +
                            "`balance` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON DELETE CASCADE)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `instruments` (" +
                            "`code` TEXT NOT NULL PRIMARY KEY, " +
                            "`note` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `instrument_code` TEXT REFERENCES `instruments`(`code`) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_instrument_code` ON `accounts` (`instrument_code`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `instruments` ADD COLUMN `decimalPlaces` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transaction_entries` ADD COLUMN `instrumentAmount` INTEGER")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transaction_entries_new` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`transactionId` TEXT NOT NULL, " +
                            "`accountId` TEXT NOT NULL, " +
                            "`debitAmount` INTEGER, " +
                            "`creditAmount` INTEGER, " +
                            "`instrumentDebitAmount` INTEGER, " +
                            "`instrumentCreditAmount` INTEGER, " +
                            "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "INSERT INTO `transaction_entries_new` SELECT `id`, `transactionId`, `accountId`, " +
                            "`debitAmount`, `creditAmount`, NULL, NULL FROM `transaction_entries`"
                )
                db.execSQL("DROP TABLE `transaction_entries`")
                db.execSQL("ALTER TABLE `transaction_entries_new` RENAME TO `transaction_entries`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_transactionId` ON `transaction_entries` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_accountId` ON `transaction_entries` (`accountId`)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `account_balances` ADD COLUMN `instrumentBalance` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rename instrument_code -> instrumentCode (SQLite <3.25 has no RENAME COLUMN)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts_new` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`name` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL, " +
                            "`instrumentCode` TEXT REFERENCES `instruments`(`code`) ON DELETE SET NULL)"
                )
                db.execSQL(
                    "INSERT INTO `accounts_new` SELECT `id`, `name`, `type`, `instrument_code` FROM `accounts`"
                )
                db.execSQL("DROP TABLE `accounts`")
                db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_instrumentCode` ON `accounts` (`instrumentCode`)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `intermediaryInstrumentCode` TEXT REFERENCES `instruments`(`code`) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_intermediaryInstrumentCode` ON `accounts` (`intermediaryInstrumentCode`)")
                db.execSQL("ALTER TABLE `transaction_entries` ADD COLUMN `intermediaryDebitAmount` INTEGER")
                db.execSQL("ALTER TABLE `transaction_entries` ADD COLUMN `intermediaryCreditAmount` INTEGER")
                db.execSQL("ALTER TABLE `account_balances` ADD COLUMN `intermediaryBalance` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite <3.25 has no RENAME COLUMN — recreate table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions_new` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`transactionDatetime` INTEGER NOT NULL, " +
                            "`note` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `transactions_new` SELECT `id`, `creationTimestamp`, `transactionDatetime`, `note` FROM `transactions`"
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Fix accounts FK constraints: SET NULL → RESTRICT, and add missing indices
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts_new` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`name` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL, " +
                            "`instrumentCode` TEXT, " +
                            "`intermediaryInstrumentCode` TEXT, " +
                            "FOREIGN KEY(`instrumentCode`) REFERENCES `instruments`(`code`) ON DELETE RESTRICT, " +
                            "FOREIGN KEY(`intermediaryInstrumentCode`) REFERENCES `instruments`(`code`) ON DELETE RESTRICT)"
                )
                db.execSQL(
                    "INSERT INTO `accounts_new` SELECT `id`, `name`, `type`, `instrumentCode`, `intermediaryInstrumentCode` FROM `accounts`"
                )
                db.execSQL("DROP TABLE `accounts`")
                db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_instrumentCode` ON `accounts` (`instrumentCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_intermediaryInstrumentCode` ON `accounts` (`intermediaryInstrumentCode`)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `instruments` ADD COLUMN `coingeckoId` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exchange_rate_cache` (" +
                            "`pairKey` TEXT NOT NULL PRIMARY KEY, " +
                            "`instrumentCode` TEXT NOT NULL, " +
                            "`intermediaryCode` TEXT NOT NULL, " +
                            "`rate` REAL NOT NULL, " +
                            "`fetchedAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `instruments` ADD COLUMN `stockApiSymbol` TEXT")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_settings` (" +
                            "`id` INTEGER NOT NULL PRIMARY KEY, " +
                            "`monthlyLivingExpenses` INTEGER NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16
                    )
                    .build().also { instance = it }
            }
        }
    }
}
