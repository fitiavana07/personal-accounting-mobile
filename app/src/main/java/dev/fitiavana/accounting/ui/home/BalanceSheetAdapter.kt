package dev.fitiavana.accounting.ui.home

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R

class BalanceSheetAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<BalanceSheetRow> = emptyList()

    fun submitList(list: List<BalanceSheetRow>) {
        rows = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is BalanceSheetRow.Title -> VIEW_TYPE_TITLE
        is BalanceSheetRow.DateLine -> VIEW_TYPE_DATE
        else -> VIEW_TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TITLE -> TitleViewHolder(inflater.inflate(R.layout.item_balance_sheet_title, parent, false))
            VIEW_TYPE_DATE -> DateViewHolder(inflater.inflate(R.layout.item_balance_sheet_date, parent, false))
            else -> RowViewHolder(inflater.inflate(R.layout.item_balance_sheet_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is BalanceSheetRow.Title -> (holder as TitleViewHolder).bind(row)
            is BalanceSheetRow.DateLine -> (holder as DateViewHolder).bind(row)
            is BalanceSheetRow.SectionHeader -> (holder as RowViewHolder).bindHeader(row)
            is BalanceSheetRow.AccountLine -> (holder as RowViewHolder).bindAccount(row)
            is BalanceSheetRow.TotalLine -> (holder as RowViewHolder).bindTotal(row)
        }
    }

    class TitleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.text_balance_sheet_title)
        fun bind(row: BalanceSheetRow.Title) {
            titleView.text = row.text
        }
    }

    class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateView: TextView = view.findViewById(R.id.text_balance_sheet_date)
        fun bind(row: BalanceSheetRow.DateLine) {
            dateView.text = row.text
        }
    }

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val labelView: TextView = view.findViewById(R.id.text_balance_sheet_label)
        private val amountView: TextView = view.findViewById(R.id.text_balance_sheet_amount)

        fun bindHeader(row: BalanceSheetRow.SectionHeader) {
            labelView.text = row.title
            amountView.text = ""
            setBold(true, 17f)
        }

        fun bindAccount(row: BalanceSheetRow.AccountLine) {
            labelView.text = row.name
            amountView.text = row.amountText
            setBold(false, 15f)
        }

        fun bindTotal(row: BalanceSheetRow.TotalLine) {
            labelView.text = row.label
            amountView.text = row.amountText
            setBold(true, 15f)
        }

        private fun setBold(bold: Boolean, textSizeSp: Float) {
            val style = if (bold) Typeface.BOLD else Typeface.NORMAL
            labelView.setTypeface(labelView.typeface, style)
            amountView.setTypeface(amountView.typeface, style)
            labelView.textSize = textSizeSp
            amountView.textSize = textSizeSp
        }
    }

    companion object {
        private const val VIEW_TYPE_TITLE = 0
        private const val VIEW_TYPE_ROW = 1
        private const val VIEW_TYPE_DATE = 2
    }
}
