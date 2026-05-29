package com.example.app4_4_api_19.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TestNote::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun testNoteDao(): TestNoteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app.db"
                ).build().also { instance = it }
            }
        }
    }
}
