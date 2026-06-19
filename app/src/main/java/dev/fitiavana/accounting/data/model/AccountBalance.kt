package dev.fitiavana.accounting.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "account_balances",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AccountBalance(
    @PrimaryKey val accountId: String,
    val balance: Int,
    val updatedAt: Long,
    val createdAt: Long
)
