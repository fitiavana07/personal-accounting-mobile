package dev.fitiavana.accounting.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R

/** Single-row set of shortcut buttons shown between the metrics and emergency fund blocks. */
class HomeShortcutsAdapter(
    private val onCexPricesClick: () -> Unit,
    private val onMonthlyExpensesClick: () -> Unit,
    private val onEarnClick: () -> Unit
): RecyclerView.Adapter<HomeShortcutsAdapter.ViewHolder>() {

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_shortcuts, parent, false)
        view.findViewById<View>(R.id.button_cex_prices).setOnClickListener { onCexPricesClick() }
        view.findViewById<View>(R.id.button_monthly_expenses)
            .setOnClickListener { onMonthlyExpensesClick() }
        view.findViewById<View>(R.id.button_earn).setOnClickListener { onEarnClick() }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = Unit

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
