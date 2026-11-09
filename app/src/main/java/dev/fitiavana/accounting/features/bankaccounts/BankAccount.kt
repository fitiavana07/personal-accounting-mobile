package dev.fitiavana.accounting.features.bankaccounts

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bank account tracked on its own, outside the double-entry accounts, in the base currency.
 * Unlike an [dev.fitiavana.accounting.features.accounts.Account], it keeps its [balance] itself:
 * the sum of its [BankAccountTransaction] amounts. [balanceDate] is the latest transaction date
 * (null while there is none).
 */
@Entity(tableName = "bank_accounts")
data class BankAccount(
    @PrimaryKey val id: String,
    val name: String,
    val balance: Long,
    val balanceDate: Long?
)

/** A single-entry movement on one bank account: [amount] is positive for a credit, negative for a debit. */
@Entity(
    tableName = "bank_account_transactions",
    foreignKeys = [
        ForeignKey(
            entity = BankAccount::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class BankAccountTransaction(
    @PrimaryKey val id: String,
    val accountId: String,
    val amount: Long,
    val note: String,
    val createdAt: Long,
    val transactionDateTime: Long
)
