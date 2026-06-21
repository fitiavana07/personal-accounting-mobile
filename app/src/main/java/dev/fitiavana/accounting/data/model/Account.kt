package dev.fitiavana.accounting.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = Instrument::class,
            parentColumns = ["code"],
            childColumns = ["instrument_code"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("instrument_code")]
)
data class Account(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    @ColumnInfo(name = "instrument_code") val instrumentCode: String? = null
)
