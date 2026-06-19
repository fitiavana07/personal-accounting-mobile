package dev.fitiavana.accounting.ui.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionsAdapter(
    private val onClick: (TransactionDisplayItem) -> Unit
) : ListAdapter<TransactionDisplayItem, TransactionsAdapter.ViewHolder>(DIFF) {

    private val amountFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
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

        holder.accounts.text = "${formatAccountList(debitAccounts)} ⇄ ${formatAccountList(creditAccounts)}"

        val totalDebit = item.entries.sumOf { it.debitAmount ?: 0.0 }
        holder.amount.text = "Ar ${amountFormat.format(totalDebit)}"

        val noteText = item.transaction.note
        if (noteText.isBlank()) {
            holder.note.visibility = View.GONE
        } else {
            holder.note.visibility = View.VISIBLE
            val firstLine = noteText.lines().first()
            val truncated = if (noteText.lines().size > 1) {
                if (firstLine.length > 60) firstLine.take(60) + "..." else firstLine + "..."
            } else {
                if (firstLine.length > 60) firstLine.take(60) + "..." else firstLine
            }
            holder.note.text = truncated
        }

        holder.date.text = dateFormat.format(Date(item.transaction.transactionDatetime))
    }

    private fun formatAccountList(names: List<String>): String {
        if (names.isEmpty()) return "?"
        return when {
            names.size <= 2 -> names.joinToString(", ")
            else -> names.take(2).joinToString(", ") + ", ..."
        }
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
