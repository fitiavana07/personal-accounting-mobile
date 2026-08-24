package dev.fitiavana.accounting.features.transactions

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val transactionDatetime: Long,
    val note: String
)
