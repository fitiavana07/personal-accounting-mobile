package dev.fitiavana.accounting.ui.chainmonitors

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitor
import dev.fitiavana.accounting.features.chainmonitors.MonitorAmount

/** One card per monitor: its label, when it fires and the holder address. Tap edits it, the bin deletes it. */
class ChainMonitorsAdapter(
    private val onClick: (ChainMonitor) -> Unit,
    private val onDelete: (ChainMonitor) -> Unit
) : RecyclerView.Adapter<ChainMonitorsAdapter.ViewHolder>() {

    private var items: List<ChainMonitor> = emptyList()

    fun submit(list: List<ChainMonitor>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder.inflate(parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position], onClick, onDelete)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.text_monitor_label)
        private val condition: TextView = view.findViewById(R.id.text_monitor_condition)
        private val holderAddress: TextView = view.findViewById(R.id.text_monitor_holder)
        private val delete: View = view.findViewById(R.id.btn_delete_monitor)

        fun bind(monitor: ChainMonitor, onClick: (ChainMonitor) -> Unit, onDelete: (ChainMonitor) -> Unit) {
            label.text = monitor.label
            condition.text = itemView.context.getString(
                R.string.chain_monitor_item_condition,
                MonitorAmount.format(monitor.thresholdValue), monitor.tokenSymbol, monitor.intervalMinutes
            )
            holderAddress.text = shorten(monitor.holderAddress)
            itemView.setOnClickListener { onClick(monitor) }
            delete.setOnClickListener { onDelete(monitor) }
        }

        companion object {
            fun inflate(parent: ViewGroup) =
                ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chain_monitor, parent, false))

            /** `0xB99B…7Cf5`: enough to recognise an address. */
            fun shorten(address: String) = if (address.length > 12) address.take(6) + "…" + address.takeLast(4) else address
        }
    }
}
