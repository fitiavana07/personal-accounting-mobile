package dev.fitiavana.accounting.features.transactions

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionWithEntries(
    @Embedded val transaction: Transaction,

    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    ) val entries: List<TransactionEntry>
)
