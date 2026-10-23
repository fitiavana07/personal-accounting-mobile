package dev.fitiavana.accounting.ui.common

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import java.util.Locale

/**
 * @param onAccountClick called with the account id when an expandable [ReportDisplayRow.AccountLine] is tapped;
 * when null (e.g. Home), account lines are not clickable.
 * @param onAprClick called with the [ReportDisplayRow.AprLine] when its underlined rate is tapped; when null, the rate is plain text.
 * @param cardStyle groups each section (header … emphasized total) into one rounded card, and tints net results
 * green/red (Reports screen); the flat look is kept otherwise (Home).
 */
class ReportAdapter(
    private val onAccountClick: ((String) -> Unit)? = null,
    private val onAprClick: ((ReportDisplayRow.AprLine) -> Unit)? = null,
    private val cardStyle: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<ReportDisplayRow> = emptyList()
    private var cardRoles: List<CardRole?> = emptyList()

    fun submitList(list: List<ReportDisplayRow>) {
        rows = list
        cardRoles = if (cardStyle) cardRolesFor(list) else emptyList()
        notifyDataSetChanged()
    }

    /** Where a row sits in its section card; null for rows outside any card. */
    internal enum class CardRole { TOP, MIDDLE, BOTTOM, SINGLE }


    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ReportDisplayRow.Title -> VIEW_TYPE_TITLE
        is ReportDisplayRow.DateLine -> VIEW_TYPE_DATE
        else -> VIEW_TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TITLE -> TitleViewHolder(inflater.inflate(R.layout.item_report_title, parent, false))
            VIEW_TYPE_DATE -> DateViewHolder(inflater.inflate(R.layout.item_report_date, parent, false))
            else -> RowViewHolder(inflater.inflate(R.layout.item_report_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ReportDisplayRow.Title -> (holder as TitleViewHolder).bind(row)
            is ReportDisplayRow.DateLine -> (holder as DateViewHolder).bind(row)
            is ReportDisplayRow.SectionHeader -> (holder as RowViewHolder).bindHeader(row)
            is ReportDisplayRow.SubsectionHeader -> (holder as RowViewHolder).bindSubsectionHeader(row)
            is ReportDisplayRow.AccountLine -> (holder as RowViewHolder).bindAccount(row, onAccountClick)
            is ReportDisplayRow.NativeLine -> (holder as RowViewHolder).bindNative(row.code, row.amountText)
            is ReportDisplayRow.AprLine -> (holder as RowViewHolder).bindApr(row, onAprClick)
            is ReportDisplayRow.TotalLine -> (holder as RowViewHolder).bindTotal(row)
        }
        if (cardStyle && holder is RowViewHolder) {
            holder.applyCard(cardRoles.getOrNull(position), rows[position])
        }
    }

    class TitleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.text_report_title)
        fun bind(row: ReportDisplayRow.Title) {
            titleView.text = row.text
        }
    }

    class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateView: TextView = view.findViewById(R.id.text_report_date)
        fun bind(row: ReportDisplayRow.DateLine) {
            dateView.text = row.text
        }
    }

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root: View = view.findViewById(R.id.layout_report_row)
        private val divider: View = view.findViewById(R.id.divider_report_row)
        private val content: View = view.findViewById(R.id.content_report_row)
        private val labelView: TextView = view.findViewById(R.id.text_report_label)
        private val amountView: TextView = view.findViewById(R.id.text_report_amount)
        private val context = view.context
        private val labelStartPadding = content.paddingStart
        private val labelIndentPadding = labelStartPadding + dpToPx(16f)

        fun bindHeader(row: ReportDisplayRow.SectionHeader) {
            labelView.text = row.title.uppercase(Locale.getDefault())
            amountView.text = ""
            setBold(true, 14f)
            setLabelIndent(labelStartPadding)
            setTextColor(ContextCompat.getColor(context, R.color.report_header_text))
            setContentVerticalPadding(10f)
            root.setBackgroundColor(ContextCompat.getColor(context, R.color.report_section_header_bg))
            resetClick()
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
            resetClick()
            divider.visibility = View.GONE
            setColorDot(row.color)
        }

        fun bindAccount(row: ReportDisplayRow.AccountLine, onAccountClick: ((String) -> Unit)?) {
            val accountId = row.accountId
            labelView.text =
                if (row.expandable) "${row.name} ${if (row.expanded) CHEVRON_EXPANDED else CHEVRON_COLLAPSED}" else row.name
            if (row.expandable && accountId != null && onAccountClick != null) {
                amountView.setOnClickListener(null)
                amountView.isClickable = false
                root.setOnClickListener { onAccountClick(accountId) }
            } else {
                resetClick()
            }
            setAmountText(row.amountText)
            setBold(false, 15f)
            setLabelIndent(labelIndentPadding)
            setTextColor(defaultTextColor())
            setContentVerticalPadding(4f)
            root.setBackgroundColor(0)
            divider.visibility = View.GONE
            setColorDot(row.color)
        }

        /** Sub-row of an expanded account (an instrument's amount or the APR): [label] and [amountText] in the same right-aligned column. */
        fun bindNative(label: String, amountText: String) {
            labelView.text = label
            setAmountText(amountText)
            setBold(false, 14f)
            setLabelIndent(labelIndentPadding + dpToPx(16f))
            setTextColor(secondaryTextColor())
            setContentVerticalPadding(2f)
            root.setBackgroundColor(0)
            resetClick()
            divider.visibility = View.GONE
            setColorDot(null)
        }

        /** Like [bindNative], but the rate is underlined and, with a handler, tapping it edits the rate. */
        fun bindApr(row: ReportDisplayRow.AprLine, onAprClick: ((ReportDisplayRow.AprLine) -> Unit)?) {
            bindNative(context.getString(R.string.report_apr_label), row.amountText)
            if (onAprClick == null || row.accountId == null) return
            // Underline the number only, not the trailing alignment space.
            amountView.text = UiUtils.underlined(row.amountText, row.amountText.trimEnd().length)
            amountView.setOnClickListener { onAprClick(row) }
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
            setAmountText(row.amountText)
            setBold(true, if (row.emphasized) 16f else 15f)
            setLabelIndent(labelStartPadding)
            setTextColor(defaultTextColor())
            setContentVerticalPadding(8f)
            root.setBackgroundColor(
                if (row.emphasized) ContextCompat.getColor(context, R.color.report_grand_total_bg) else 0
            )
            resetClick()
            divider.visibility = View.VISIBLE
            setColorDot(null)
        }

        /** Card style: [role]'s background and outer margins (reset on every bind: views are recycled), plus net-result tint. */
        internal fun applyCard(role: CardRole?, row: ReportDisplayRow) {
            val background = when (role) {
                CardRole.TOP -> R.drawable.bg_report_card_top
                CardRole.MIDDLE -> R.drawable.bg_report_card_middle
                CardRole.BOTTOM -> R.drawable.bg_report_card_bottom
                CardRole.SINGLE -> R.drawable.bg_report_card_single
                null -> null
            }
            if (background != null) root.setBackgroundResource(background)
            val sideMarginPx = if (role != null) dpToPx(16f) else 0
            val topMarginPx = if (role == CardRole.TOP || role == CardRole.SINGLE) dpToPx(12f) else 0
            (itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.setMargins(sideMarginPx, topMarginPx, sideMarginPx, 0)
                itemView.layoutParams = it
            }
            if (row is ReportDisplayRow.TotalLine && row.emphasized && row.netResultSign != null) {
                amountView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (row.netResultSign < 0) R.color.report_net_loss else R.color.report_net_gain
                    )
                )
            }
        }

        private fun resetClick() {
            root.setOnClickListener(null)
            root.isClickable = false
            amountView.setOnClickListener(null)
            amountView.isClickable = false
        }

        private fun setLabelIndent(startPadding: Int) {
            labelView.setPaddingRelative(startPadding, labelView.paddingTop, labelView.paddingEnd, labelView.paddingBottom)
        }

        private fun setContentVerticalPadding(verticalDp: Float) {
            val verticalPx = dpToPx(verticalDp)
            content.setPaddingRelative(content.paddingStart, verticalPx, content.paddingEnd, verticalPx)
        }

        /**
         * Right-aligns the digits of every amount in one column: a parenthesized (contra) amount
         * keeps its closing ")" in the margin, the others reserve that same width.
         */
        private fun setAmountText(text: String) {
            amountView.text = text.trimEnd()
            reserveParenWidth()
        }

        /** Also called after the text size changes, since the reserved width depends on it. */
        private fun reserveParenWidth() {
            val parenWidth = if (amountView.text.startsWith("(")) 0 else amountView.paint.measureText(")").toInt()
            amountView.setPaddingRelative(0, 0, parenWidth, 0)
        }

        private fun setBold(bold: Boolean, textSizeSp: Float) {
            val style = if (bold) Typeface.BOLD else Typeface.NORMAL
            labelView.setTypeface(Typeface.DEFAULT, style)
            amountView.setTypeface(Typeface.DEFAULT, style)
            // Tabular figures keep digits aligned in a proportional font (no-op below API 21).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) amountView.fontFeatureSettings = "tnum"
            labelView.textSize = textSizeSp
            amountView.textSize = textSizeSp
            reserveParenWidth()
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
        /** A card runs from a [ReportDisplayRow.SectionHeader] to the next emphasized total; a lone emphasized total (Net Income) is its own card. */
        internal fun cardRolesFor(rows: List<ReportDisplayRow>): List<CardRole?> {
            var inCard = false
            return rows.map { row ->
                when {
                    row is ReportDisplayRow.SectionHeader -> CardRole.TOP.also { inCard = true }
                    row is ReportDisplayRow.TotalLine && row.emphasized ->
                        (if (inCard) CardRole.BOTTOM else CardRole.SINGLE).also { inCard = false }
                    inCard && row !is ReportDisplayRow.Title && row !is ReportDisplayRow.DateLine -> CardRole.MIDDLE
                    else -> null
                }
            }
        }

        private const val VIEW_TYPE_TITLE = 0
        private const val VIEW_TYPE_ROW = 1
        private const val VIEW_TYPE_DATE = 2
        // ▸/▾ (U+25B8/U+25BE) are missing from API 19's system font; ►/▼ render everywhere.
        private const val CHEVRON_COLLAPSED = "►"
        private const val CHEVRON_EXPANDED = "▼"
    }
}
