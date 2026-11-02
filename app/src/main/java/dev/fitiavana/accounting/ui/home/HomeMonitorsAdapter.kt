package dev.fitiavana.accounting.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.chainmonitors.MonitorAmount
import dev.fitiavana.accounting.features.chainmonitors.MonitorRow

/**
 * Single-row block after the P2P prices: one line per chain monitor with its label and last balance
 * ("–" before the first reading). Hidden when there is no monitor; the header ([onHeaderClick]) opens the screen.
 */
class HomeMonitorsAdapter(
    private val onHeaderClick: () -> Unit
) : RecyclerView.Adapter<HomeMonitorsAdapter.ViewHolder>() {

    private var rows: List<MonitorRow> = emptyList()

    fun submit(rows: List<MonitorRow>) {
        val wasVisible = itemCount == 1
        this.rows = rows
        when {
            rows.isEmpty() && wasVisible -> notifyItemRemoved(0)
            rows.isNotEmpty() && !wasVisible -> notifyItemInserted(0)
            wasVisible -> notifyItemChanged(0)
        }
    }

    override fun getItemCount() = if (rows.isEmpty()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_monitors, parent, false)
        view.findViewById<View>(R.id.text_home_monitors_header).setOnClickListener { onHeaderClick() }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(rows)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: ViewGroup = view.findViewById(R.id.container_home_monitors_rows)

        fun bind(rows: List<MonitorRow>) {
            container.removeAllViews()
            val inflater = LayoutInflater.from(container.context)
            rows.forEach { row ->
                val line = inflater.inflate(R.layout.item_home_monitor_row, container, false)
                line.findViewById<TextView>(R.id.text_monitor_row_label).text = row.monitor.label
                line.findViewById<TextView>(R.id.text_monitor_row_amount).text =
                    MonitorAmount.format(row.lastBalance)
                        ?.let { container.context.getString(R.string.home_monitor_amount, it, row.monitor.tokenSymbol) }
                        ?: PLACEHOLDER
                container.addView(line)
            }
        }
    }

    private companion object {
        const val PLACEHOLDER = "–"
    }
}
