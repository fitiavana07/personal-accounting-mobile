package dev.fitiavana.accounting.ui.transactions

/** Display-ready row for the transaction detail entries table. */
sealed class TransactionDetailRow {
    data class Entry(
        val accountName: String,
        val debitText: String,
        val creditText: String
    ) : TransactionDetailRow()

    data class SubEntry(
        val label: String,
        val debitText: String,
        val creditText: String
    ) : TransactionDetailRow()

    data class Total(
        val debitAmount: Long,
        val creditAmount: Long
    ) : TransactionDetailRow()
}

/** Display-ready model for [TransactionDetailActivity], built by [TransactionDetailPresenter]. */
data class TransactionDetailView(
    val totalAmount: Long,
    val note: String,
    val rows: List<TransactionDetailRow>
)