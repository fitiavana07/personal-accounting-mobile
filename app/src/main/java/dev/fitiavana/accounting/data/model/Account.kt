package dev.fitiavana.accounting.data.model

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
            childColumns = ["instrumentCode"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("instrumentCode")]
)
data class Account(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val instrumentCode: String? = null
)
