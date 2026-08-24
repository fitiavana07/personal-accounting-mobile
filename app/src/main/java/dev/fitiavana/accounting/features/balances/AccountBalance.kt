package dev.fitiavana.accounting.features.balances

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import dev.fitiavana.accounting.features.accounts.Account

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
    val balance: Long,
    val instrumentBalance: Long = 0,
    val intermediaryBalance: Long = 0,
    val updatedAt: Long,
    val createdAt: Long
)
