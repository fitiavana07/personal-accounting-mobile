package dev.fitiavana.accounting.ui.common

import android.graphics.Paint
import android.graphics.Typeface
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
        val wrapsValueCells: Boolean = false
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
            wrapsValueCells = true
        )
        val dataRows = display.rows.map {
            Row(listOf(it.label) + it.cellTexts, emphasized = it.emphasized, topDivider = it.emphasized)
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
        private val divider: View = view.findViewById(R.id.divider_equity_row)
        private val content: LinearLayout = view.findViewById(R.id.content_equity_row)
        private val context = view.context

        internal fun bind(row: Row, labelColumnWidthPx: Int) {
            divider.visibility = if (row.topDivider) View.VISIBLE else View.GONE
            content.removeAllViews()
            row.cellTexts.forEachIndexed { index, text ->
                val isLabel = index == 0
                content.addView(
                    cellView(
                        text,
                        isLabel = isLabel,
                        bold = row.emphasized,
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
                typeface = Typeface.MONOSPACE
                textSize = spToPx(VALUE_TEXT_SIZE_SP)
            }.measureText("0")
            return dpToPx(VALUE_WIDTH_DP) + (charWidthPx * EXTRA_VALUE_WIDTH_CHARS).toInt()
        }

        private fun labelHorizontalPaddingPx(): Int = dpToPx(LABEL_PADDING_START_DP + LABEL_PADDING_END_DP)

        private fun cellView(
            text: String,
            isLabel: Boolean,
            bold: Boolean,
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
                typeface = if (isLabel) Typeface.DEFAULT else Typeface.MONOSPACE
                setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
                maxLines = if (wraps) HEADER_VALUE_MAX_LINES else 1
                setTextColor(defaultTextColor())
            }

        private fun defaultTextColor(): Int {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
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
