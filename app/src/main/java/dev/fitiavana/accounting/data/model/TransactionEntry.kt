package dev.fitiavana.accounting.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_entries",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("transactionId"),
        Index("accountId")
    ]
)
data class TransactionEntry(
    @PrimaryKey val id: String,
    val transactionId: String,
    val accountId: String,
    val debitAmount: Int?,
    val creditAmount: Int?,
    val instrumentAmount: Long? = null
)
