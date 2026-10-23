package dev.fitiavana.accounting.ui.common

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R

/**
 * Renders [EquityStatementDisplay] as a table: a header row of column
 * titles followed by one row per [EquityStatementDisplayRow]. Since the
 * number of columns is data-dependent, cells are added programmatically
 * with a fixed per-column width so every row (including the header) lines
 * up; the containing view is expected to scroll horizontally (see
 * fragment_reports.xml) when the table is wider than the screen.
 */
class EquityStatementAdapter : RecyclerView.Adapter<EquityStatementAdapter.RowViewHolder>() {

    internal data class Row(
        val cellTexts: List<String>,
        val emphasized: Boolean,
        val topDivider: Boolean,
        // Only the header row's value cells (column titles) wrap onto multiple lines;
        // every other cell, including the leftmost/label column on every row, stays single-line.
        val wrapsValueCells: Boolean = false,
        // Card background drawable: the table is one rounded card (header on top, final balance at the
        // bottom), with the Starting/Ending balance rows tinted.
        val backgroundRes: Int,
        // Column titles are muted; every other row uses the primary text colour.
        val muted: Boolean = false
    )

    private var rows: List<Row> = emptyList()

    // Recomputed (from the current [rows]) by the first bind after each submitList,
    // so the leftmost column is exactly as wide as its longest label — no ellipsis needed.
    private var labelColumnWidthPx: Int? = null

    fun submitList(display: EquityStatementDisplay) {
        val headerRow = Row(
            listOf("") + display.columnTitles,
            emphasized = true,
            topDivider = false,
            wrapsValueCells = true,
            backgroundRes = R.drawable.bg_report_card_top,
            muted = true
        )
        val dataRows = display.rows.mapIndexed { index, it ->
            Row(
                listOf(it.label) + it.cellTexts,
                emphasized = it.emphasized,
                topDivider = it.emphasized,
                backgroundRes = when {
                    index == display.rows.lastIndex -> R.drawable.bg_report_card_bottom
                    it.emphasized -> R.drawable.bg_report_card_footer
                    else -> R.drawable.bg_report_card_middle
                }
            )
        }
        rows = listOf(headerRow) + dataRows
        labelColumnWidthPx = null
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder =
        RowViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_equity_row, parent, false)
        )

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val labelWidthPx = labelColumnWidthPx ?: holder.measureLabelColumnWidthPx(rows.map { it.cellTexts[0] })
            .also { labelColumnWidthPx = it }
        holder.bind(rows[position], labelWidthPx)
    }

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val root: View = view.findViewById(R.id.layout_equity_row)
        private val divider: View = view.findViewById(R.id.divider_equity_row)
        private val content: LinearLayout = view.findViewById(R.id.content_equity_row)
        private val context = view.context

        internal fun bind(row: Row, labelColumnWidthPx: Int) {
            divider.visibility = if (row.topDivider) View.VISIBLE else View.GONE
            root.setBackgroundResource(row.backgroundRes)
            content.removeAllViews()
            row.cellTexts.forEachIndexed { index, text ->
                val isLabel = index == 0
                content.addView(
                    cellView(
                        text,
                        isLabel = isLabel,
                        bold = row.emphasized,
                        muted = row.muted || text == "-",
                        wraps = row.wrapsValueCells && !isLabel,
                        widthPx = if (isLabel) labelColumnWidthPx else valueColumnWidthPx()
                    )
                )
            }
        }

        /** The label column's width: just wide enough to fit [labels]' longest entry on one line, no ellipsis. */
        internal fun measureLabelColumnWidthPx(labels: List<String>): Int {
            val paint = Paint().apply {
                // Bold typeface, since the emphasized "Balance at ..." rows render bold and are
                // therefore wider than a regular-weight label of the same text.
                typeface = Typeface.DEFAULT_BOLD
                textSize = spToPx(LABEL_TEXT_SIZE_SP)
            }
            val longestTextWidthPx = labels.maxOf { paint.measureText(it) }
            return longestTextWidthPx.toInt() + labelHorizontalPaddingPx() + dpToPx(WIDTH_BUFFER_DP)
        }

        private fun valueColumnWidthPx(): Int {
            val charWidthPx = Paint().apply {
                typeface = Typeface.DEFAULT
                textSize = spToPx(VALUE_TEXT_SIZE_SP)
            }.measureText("0")
            return dpToPx(VALUE_WIDTH_DP) + (charWidthPx * EXTRA_VALUE_WIDTH_CHARS).toInt()
        }

        private fun labelHorizontalPaddingPx(): Int = dpToPx(LABEL_PADDING_START_DP + LABEL_PADDING_END_DP)

        private fun cellView(
            text: String,
            isLabel: Boolean,
            bold: Boolean,
            muted: Boolean,
            wraps: Boolean,
            widthPx: Int
        ): TextView =
            TextView(context).apply {
                this.text = text
                layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = if (isLabel) Gravity.START else Gravity.END
                setPaddingRelative(
                    dpToPx(if (isLabel) LABEL_PADDING_START_DP else 4f),
                    0,
                    dpToPx(if (isLabel) LABEL_PADDING_END_DP else 4f),
                    0
                )
                textSize = if (isLabel) LABEL_TEXT_SIZE_SP else VALUE_TEXT_SIZE_SP
                setTypeface(Typeface.DEFAULT, if (bold || (muted && !isLabel && wraps)) Typeface.BOLD else Typeface.NORMAL)
                // Tabular figures keep digits aligned in a proportional font (no-op below API 21).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) fontFeatureSettings = "tnum"
                maxLines = if (wraps) HEADER_VALUE_MAX_LINES else 1
                setTextColor(if (muted) secondaryTextColor() else defaultTextColor())
            }

        private fun defaultTextColor(): Int = themeTextColor(android.R.attr.textColorPrimary)

        private fun secondaryTextColor(): Int = themeTextColor(android.R.attr.textColorSecondary)

        private fun themeTextColor(attr: Int): Int {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(attr, typedValue, true)
            return if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }

        private fun dpToPx(dp: Float): Int =
            (dp * context.resources.displayMetrics.density).toInt()

        private fun spToPx(sp: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
    }

    companion object {
        private const val LABEL_TEXT_SIZE_SP = 14f
        private const val VALUE_TEXT_SIZE_SP = 14f
        private const val LABEL_PADDING_START_DP = 16f
        private const val LABEL_PADDING_END_DP = 8f
        // Small safety margin on top of the measured text width, absorbing any rounding
        // difference between Paint.measureText and the TextView's actual text layout.
        private const val WIDTH_BUFFER_DP = 4f
        private const val VALUE_WIDTH_DP = 110f
        private const val EXTRA_VALUE_WIDTH_CHARS = 3
        private const val HEADER_VALUE_MAX_LINES = 3
    }
}
