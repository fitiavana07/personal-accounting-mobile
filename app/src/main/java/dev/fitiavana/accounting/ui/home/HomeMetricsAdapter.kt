package dev.fitiavana.accounting.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R

/** Top-of-home-screen block showing equity, cash and emergency fund progress at a glance. */
class HomeMetricsAdapter : RecyclerView.Adapter<HomeMetricsAdapter.ViewHolder>() {

    private var metrics = HomeMetrics(
        totalEquity = 0,
        cash = 0,
        emergencyFundPercent = 100,
        cashToEquityPercent = 0,
        monthlyExpenses = 0,
        cashRunwayMonths = 0.0
    )

    fun submit(metrics: HomeMetrics) {
        this.metrics = metrics
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_metrics, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(metrics)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val equityView: TextView =
            view.findViewById(R.id.text_metric_equity_value)
        private val cashView: TextView =
            view.findViewById(R.id.text_metric_cash_value)
        private val emergencyFundView: TextView =
            view.findViewById(R.id.text_metric_emergency_fund_value)
        private val cashToEquityView: TextView =
            view.findViewById(R.id.text_metric_cash_to_equity_value)
        private val monthlyExpenseView: TextView =
            view.findViewById(R.id.text_metric_monthly_expense_value)
        private val cashRunwayView: TextView =
            view.findViewById(R.id.text_metric_cash_runway_value)
        private val context = view.context

        fun bind(metrics: HomeMetrics) {
            equityView.text = context.getString(
                R.string.amount_ar,
                CompactNumberFormatter.format(metrics.totalEquity)
            )
            cashView.text = context.getString(
                R.string.amount_ar,
                CompactNumberFormatter.format(metrics.cash)
            )
            emergencyFundView.text = context.getString(
                R.string.home_metric_percent_format,
                metrics.emergencyFundPercent
            )
            cashToEquityView.text = context.getString(
                R.string.home_metric_percent_format,
                metrics.cashToEquityPercent
            )
            monthlyExpenseView.text = context.getString(
                R.string.amount_ar,
                CompactNumberFormatter.format(metrics.monthlyExpenses)
            )
            cashRunwayView.text = context.getString(
                R.string.home_metric_runway_format,
                String.format("%.1f", metrics.cashRunwayMonths)
            )
        }
    }
}
