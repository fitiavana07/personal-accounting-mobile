package dev.fitiavana.accounting.features.transactions

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val transactionDatetime: Long,
    val note: String,
    /** Base-currency units per whole unit of the sold instrument (sell-to-base swap); null otherwise. */
    @ColumnInfo(name = "exchange_rate") val exchangeRate: Double? = null
)
