package dev.fitiavana.accounting.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.fitiavana.accounting.data.dao.AccountBalanceDao
import dev.fitiavana.accounting.data.dao.AccountDao
import dev.fitiavana.accounting.data.dao.InstrumentDao
import dev.fitiavana.accounting.data.dao.TransactionDao
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.model.Transaction
import dev.fitiavana.accounting.data.model.TransactionEntry

@Database(
    entities = [Account::class, Transaction::class, TransactionEntry::class, AccountBalance::class, Instrument::class],
    version = 8
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun accountBalanceDao(): AccountBalanceDao
    abstract fun instrumentDao(): InstrumentDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`creationTimestamp` INTEGER NOT NULL, " +
                            "`transactionDatetime` INTEGER NOT NULL, " +
                            "`note` TEXT NOT NULL)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transaction_entries` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`transactionId` TEXT NOT NULL, " +
                            "`accountId` TEXT NOT NULL, " +
                            "`debitAmount` REAL, " +
                            "`creditAmount` REAL, " +
                            "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON DELETE RESTRICT)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_transactionId` ON `transaction_entries` (`transactionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_accountId` ON `transaction_entries` (`accountId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transaction_entries_new` (" +
                            "`id` TEXT NOT NULL PRIMARY KEY, " +
                            "`transactionId` TEXT NOT NULL, " +
                            "`accountId` TEXT NOT NULL, " +
                            "`debitAmount` INTEGER, " +
                            "`creditAmount` INTEGER, " +
                            "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON DELETE RESTRICT)"
                )
                database.execSQL(
                    "INSERT INTO `transaction_entries_new` SELECT `id`, `transactionId`, `accountId`, " +
                            "CAST(`debitAmount` AS INTEGER), CAST(`creditAmount` AS INTEGER) FROM `transaction_entries`"
                )
                database.execSQL("DROP TABLE `transaction_entries`")
                database.execSQL("ALTER TABLE `transaction_entries_new` RENAME TO `transaction_entries`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_transactionId` ON `transaction_entries` (`transactionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_accountId` ON `transaction_entries` (`accountId`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `accounts` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'asset'")
                database.execSQL(
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
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `instruments` (" +
                            "`code` TEXT NOT NULL PRIMARY KEY, " +
                            "`note` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `accounts` ADD COLUMN `instrument_code` TEXT REFERENCES `instruments`(`code`) ON DELETE SET NULL")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_instrument_code` ON `accounts` (`instrument_code`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `instruments` ADD COLUMN `decimalPlaces` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `transaction_entries` ADD COLUMN `instrumentAmount` INTEGER")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
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
                database.execSQL(
                    "INSERT INTO `transaction_entries_new` SELECT `id`, `transactionId`, `accountId`, " +
                            "`debitAmount`, `creditAmount`, NULL, NULL FROM `transaction_entries`"
                )
                database.execSQL("DROP TABLE `transaction_entries`")
                database.execSQL("ALTER TABLE `transaction_entries_new` RENAME TO `transaction_entries`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_transactionId` ON `transaction_entries` (`transactionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_entries_accountId` ON `transaction_entries` (`accountId`)")
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
                        MIGRATION_7_8
                    )
                    .build().also { instance = it }
            }
        }
    }
}
