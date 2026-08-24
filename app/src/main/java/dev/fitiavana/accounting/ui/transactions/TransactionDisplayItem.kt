package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry

data class TransactionDisplayItem(
    val transaction: Transaction,
    val entries: List<TransactionEntry>,
    val accountsMap: Map<String, String>
)
