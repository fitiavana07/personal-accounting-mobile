package dev.fitiavana.accounting.ui.alerts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.alerts.AlertTypes
import dev.fitiavana.accounting.features.balances.SignedPercent

/** One card per alert: the account and when it fires. Tap edits it, the bin deletes it. */
class AlertsAdapter(
    private val onClick: (AlertItem) -> Unit,
    private val onDelete: (AlertItem) -> Unit
) : RecyclerView.Adapter<AlertsAdapter.ViewHolder>() {

    private var items: List<AlertItem> = emptyList()

    fun submit(list: List<AlertItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_alert, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.account.text = item.accountName
        holder.condition.text = context.getString(
            if (item.alert.type == AlertTypes.CROSSING_ABOVE) R.string.alert_item_above else R.string.alert_item_below,
            SignedPercent.format(item.alert.thresholdPercent)
        )
        holder.itemView.setOnClickListener { onClick(item) }
        holder.delete.setOnClickListener { onDelete(item) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val account: TextView = view.findViewById(R.id.text_alert_account)
        val condition: TextView = view.findViewById(R.id.text_alert_condition)
        val delete: View = view.findViewById(R.id.btn_delete_alert)
    }
}
