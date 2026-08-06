package dev.fitiavana.accounting.ui.reports

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R

/**
 * Horizontal scrollable selector of values (years, months as 0-11, or report types), used
 * for the year, month and report type rows on the Reports screen.
 */
class PeriodSelectorAdapter<T>(
    private val labelFor: (T) -> String,
    private val onSelected: (T) -> Unit
) : RecyclerView.Adapter<PeriodSelectorAdapter<T>.ViewHolder>() {

    private var items: List<T> = emptyList()
    private var selected: T? = null

    fun submitList(items: List<T>, selected: T?) {
        this.items = items
        this.selected = selected
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_period_selector, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], items[position] == selected)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.text_period_selector)
        private val underline: View = view.findViewById(R.id.underline_period_selector)

        fun bind(value: T, isSelected: Boolean) {
            textView.text = labelFor(value)
            textView.setTypeface(Typeface.DEFAULT, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            textView.setTextColor(
                if (isSelected) {
                    ContextCompat.getColor(textView.context, R.color.gold_500)
                } else {
                    defaultTextColor(textView.context)
                }
            )
            underline.setBackgroundColor(
                if (isSelected) ContextCompat.getColor(underline.context, R.color.gold_500) else 0
            )
            itemView.setOnClickListener { onSelected(value) }
        }

        private fun defaultTextColor(context: android.content.Context): Int {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            return if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }
    }
}
