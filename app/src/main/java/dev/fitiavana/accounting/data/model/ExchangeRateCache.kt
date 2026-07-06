package dev.fitiavana.accounting.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exchange_rate_cache")
data class ExchangeRateCache(
    @PrimaryKey val pairKey: String,
    val instrumentCode: String,
    val intermediaryCode: String,
    val rate: Double,
    val fetchedAt: Long
) {
    companion object {
        fun pairKey(instrumentCode: String, intermediaryCode: String) = "$instrumentCode:$intermediaryCode"
    }
}
