package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.data.model.Transaction
import dev.fitiavana.accounting.data.model.TransactionEntry

data class TransactionDisplayItem(
    val transaction: Transaction,
    val entries: List<TransactionEntry>,
    val accountsMap: Map<String, String>
)
