package dev.fitiavana.accounting.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

/** A fresh in-memory database for Robolectric tests; callers close it. */
fun inMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
