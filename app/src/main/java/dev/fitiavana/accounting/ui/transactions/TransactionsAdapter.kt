package dev.fitiavana.accounting.ui.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionsAdapter(
    private val onClick: (TransactionDisplayItem) -> Unit
) : ListAdapter<TransactionDisplayItem, TransactionsAdapter.ViewHolder>(DIFF) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val accounts: TextView = view.findViewById(R.id.text_accounts)
        val amount: TextView = view.findViewById(R.id.text_amount)
        val note: TextView = view.findViewById(R.id.text_note)
        val date: TextView = view.findViewById(R.id.text_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.setOnClickListener { onClick(item) }

        val debitAccounts = item.entries
            .filter { it.debitAmount != null }
            .mapNotNull { item.accountsMap[it.accountId] }
        val creditAccounts = item.entries
            .filter { it.creditAmount != null }
            .mapNotNull { item.accountsMap[it.accountId] }

        holder.accounts.text = "${TransactionDisplay.formatAccountList(debitAccounts)} ⇄ ${TransactionDisplay.formatAccountList(creditAccounts)}"

        val totalDebit = item.entries.sumOf { it.debitAmount ?: 0L }
        holder.amount.text = "Ar ${TransactionDisplay.formatAmount(totalDebit)}"

        val notePreview = TransactionDisplay.formatNotePreview(item.transaction.note)
        if (notePreview.isEmpty()) {
            holder.note.visibility = View.GONE
        } else {
            holder.note.visibility = View.VISIBLE
            holder.note.text = notePreview
        }

        holder.date.text = dateFormat.format(Date(item.transaction.transactionDatetime))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TransactionDisplayItem>() {
            override fun areItemsTheSame(a: TransactionDisplayItem, b: TransactionDisplayItem) =
                a.transaction.id == b.transaction.id

            override fun areContentsTheSame(a: TransactionDisplayItem, b: TransactionDisplayItem) =
                a == b
        }
    }
}
