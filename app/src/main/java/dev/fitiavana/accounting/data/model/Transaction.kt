package dev.fitiavana.accounting.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String,
    val creationTimestamp: Long,
    val transactionDatetime: Long,
    val note: String
)
