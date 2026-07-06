package dev.fitiavana.accounting.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "instruments")
data class Instrument(
    @PrimaryKey val code: String,
    val note: String,
    val type: String,
    val decimalPlaces: Int = 0,
    val coingeckoId: String? = null
)