package dev.fitiavana.accounting.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.fitiavana.accounting.data.dao.AccountDao
import dev.fitiavana.accounting.data.model.Account
import java.util.UUID

@Database(entities = [Account::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val SEED_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                listOf("Aave3 USDC Base", "Binance RWUSD", "CEM").forEach { name ->
                    db.execSQL(
                        "INSERT INTO accounts (id, name) VALUES (?, ?)",
                        arrayOf(UUID.randomUUID().toString(), name)
                    )
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app.db"
                )
                .addCallback(SEED_CALLBACK)
                .build().also { instance = it }
            }
        }
    }
}