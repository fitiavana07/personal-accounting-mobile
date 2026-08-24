package dev.fitiavana.accounting.features.accounts

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.fitiavana.accounting.features.instruments.Instrument

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = Instrument::class,
            parentColumns = ["code"],
            childColumns = ["instrumentCode"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Instrument::class,
            parentColumns = ["code"],
            childColumns = ["intermediaryInstrumentCode"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("instrumentCode"), Index("intermediaryInstrumentCode")]
)
data class Account(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val instrumentCode: String? = null,
    val intermediaryInstrumentCode: String? = null
)
