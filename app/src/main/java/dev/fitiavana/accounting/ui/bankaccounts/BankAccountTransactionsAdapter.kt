package dev.fitiavana.accounting.ui.bankaccounts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.bankaccounts.BankAccountTransaction
import dev.fitiavana.accounting.ui.common.UiUtils

/** The transactions of one bank account: date, note, the signed amount and the balance after it (green credit, red debit). Not editable. */
class BankAccountTransactionsAdapter :
    ListAdapter<BankAccountTransactionRow, BankAccountTransactionsAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_bank_account_transaction, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val date: TextView = view.findViewById(R.id.text_bank_tx_date)
        private val note: TextView = view.findViewById(R.id.text_bank_tx_note)
        private val amount: TextView = view.findViewById(R.id.text_bank_tx_amount)

        private val balance: TextView = view.findViewById(R.id.text_bank_tx_balance)

        fun bind(row: BankAccountTransactionRow) {
            val transaction = row.transaction
            val context = itemView.context
            date.text = BankAccountDisplay.formatDate(transaction.transactionDateTime)
            note.text = transaction.note
            note.visibility = if (transaction.note.isBlank()) View.GONE else View.VISIBLE
            val credit = transaction.amount > 0
            amount.text = context.getString(
                if (credit) R.string.bank_transaction_credit_amount else R.string.bank_transaction_debit_amount,
                UiUtils.formatAmountAr(context, Math.abs(transaction.amount))
            )
            balance.text = context.getString(
                R.string.bank_transaction_balance_after,
                UiUtils.formatAmountAr(context, row.balanceAfter)
            )
            amount.setTextColor(ContextCompat.getColor(context, if (credit) R.color.gain else R.color.loss))
        }
    }

    private object Diff : DiffUtil.ItemCallback<BankAccountTransactionRow>() {
        override fun areItemsTheSame(a: BankAccountTransactionRow, b: BankAccountTransactionRow) = a.transaction.id == b.transaction.id
        override fun areContentsTheSame(a: BankAccountTransactionRow, b: BankAccountTransactionRow) = a == b
    }
}
