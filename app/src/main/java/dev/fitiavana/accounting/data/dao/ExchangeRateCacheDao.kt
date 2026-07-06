package dev.fitiavana.accounting.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.fitiavana.accounting.data.model.ExchangeRateCache

@Dao
interface ExchangeRateCacheDao {
    @Query("SELECT * FROM exchange_rate_cache")
    fun getAll(): LiveData<List<ExchangeRateCache>>

    @Query("SELECT * FROM exchange_rate_cache")
    fun getAllSync(): List<ExchangeRateCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(rate: ExchangeRateCache)
}
