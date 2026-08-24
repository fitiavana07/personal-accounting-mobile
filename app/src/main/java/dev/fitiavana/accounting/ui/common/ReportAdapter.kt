package dev.fitiavana.accounting.ui.common

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import java.util.Locale

class ReportAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<ReportDisplayRow> = emptyList()

    fun submitList(list: List<ReportDisplayRow>) {
        rows = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ReportDisplayRow.Title -> VIEW_TYPE_TITLE
        is ReportDisplayRow.DateLine -> VIEW_TYPE_DATE
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
            is ReportDisplayRow.Title -> (holder as TitleViewHolder).bind(row)
            is ReportDisplayRow.DateLine -> (holder as DateViewHolder).bind(row)
            is ReportDisplayRow.SectionHeader -> (holder as RowViewHolder).bindHeader(row)
            is ReportDisplayRow.SubsectionHeader -> (holder as RowViewHolder).bindSubsectionHeader(row)
            is ReportDisplayRow.AccountLine -> (holder as RowViewHolder).bindAccount(row)
            is ReportDisplayRow.TotalLine -> (holder as RowViewHolder).bindTotal(row)
        }
    }

    class TitleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.text_balance_sheet_title)
        fun bind(row: ReportDisplayRow.Title) {
            titleView.text = row.text
        }
    }

    class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateView: TextView = view.findViewById(R.id.text_balance_sheet_date)
        fun bind(row: ReportDisplayRow.DateLine) {
            dateView.text = row.text
        }
    }

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root: View = view.findViewById(R.id.layout_balance_sheet_row)
        private val divider: View = view.findViewById(R.id.divider_balance_sheet_row)
        private val content: View = view.findViewById(R.id.content_balance_sheet_row)
        private val labelView: TextView = view.findViewById(R.id.text_balance_sheet_label)
        private val amountView: TextView = view.findViewById(R.id.text_balance_sheet_amount)
        private val context = view.context
        private val labelStartPadding = content.paddingStart
        private val labelIndentPadding = labelStartPadding + dpToPx(16f)

        fun bindHeader(row: ReportDisplayRow.SectionHeader) {
            labelView.text = row.title.uppercase(Locale.getDefault())
            amountView.text = ""
            setBold(true, 14f)
            setLabelIndent(labelStartPadding)
            setTextColor(ContextCompat.getColor(context, R.color.bs_header_text))
            setContentVerticalPadding(10f)
            root.setBackgroundColor(ContextCompat.getColor(context, R.color.bs_section_header_bg))
            divider.visibility = View.GONE
            setColorDot(null)
        }

        fun bindSubsectionHeader(row: ReportDisplayRow.SubsectionHeader) {
            labelView.text = row.title
            amountView.text = ""
            setBold(true, 14f)
            setLabelIndent(labelStartPadding)
            setTextColor(secondaryTextColor())
            setContentVerticalPadding(6f)
            root.setBackgroundColor(0)
            divider.visibility = View.GONE
            setColorDot(null)
        }

        fun bindAccount(row: ReportDisplayRow.AccountLine) {
            labelView.text = row.name
            amountView.text = row.amountText
            setBold(false, 15f)
            setLabelIndent(labelIndentPadding)
            setTextColor(defaultTextColor())
            setContentVerticalPadding(4f)
            root.setBackgroundColor(0)
            divider.visibility = View.GONE
            setColorDot(row.color)
        }

        private fun setColorDot(color: Int?) {
            val dot = color?.let { colorDotDrawable(it) }
            labelView.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
            labelView.compoundDrawablePadding = if (dot != null) dpToPx(8f) else 0
        }

        private fun colorDotDrawable(color: Int): Drawable {
            val sizePx = dpToPx(10f)
            return ShapeDrawable(OvalShape()).apply {
                setIntrinsicWidth(sizePx)
                setIntrinsicHeight(sizePx)
                paint.color = color
            }
        }

        fun bindTotal(row: ReportDisplayRow.TotalLine) {
            labelView.text = row.label
            amountView.text = row.amountText
            setBold(true, if (row.emphasized) 16f else 15f)
            setLabelIndent(labelStartPadding)
            setTextColor(defaultTextColor())
            setContentVerticalPadding(8f)
            root.setBackgroundColor(
                if (row.emphasized) ContextCompat.getColor(context, R.color.bs_grand_total_bg) else 0
            )
            divider.visibility = View.VISIBLE
            setColorDot(null)
        }

        private fun setLabelIndent(startPadding: Int) {
            labelView.setPaddingRelative(startPadding, labelView.paddingTop, labelView.paddingEnd, labelView.paddingBottom)
        }

        private fun setContentVerticalPadding(verticalDp: Float) {
            val verticalPx = dpToPx(verticalDp)
            content.setPaddingRelative(content.paddingStart, verticalPx, content.paddingEnd, verticalPx)
        }

        private fun setBold(bold: Boolean, textSizeSp: Float) {
            val style = if (bold) Typeface.BOLD else Typeface.NORMAL
            labelView.setTypeface(Typeface.DEFAULT, style)
            amountView.setTypeface(Typeface.MONOSPACE, style)
            labelView.textSize = textSizeSp
            amountView.textSize = textSizeSp
        }

        private fun setTextColor(color: Int) {
            labelView.setTextColor(color)
            amountView.setTextColor(color)
        }

        private fun defaultTextColor(): Int {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            return if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }

        private fun secondaryTextColor(): Int {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
            return if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }

        private fun dpToPx(dp: Float): Int =
            (dp * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val VIEW_TYPE_TITLE = 0
        private const val VIEW_TYPE_ROW = 1
        private const val VIEW_TYPE_DATE = 2
    }
}
