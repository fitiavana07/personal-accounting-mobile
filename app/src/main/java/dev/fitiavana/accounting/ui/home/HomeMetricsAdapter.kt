package dev.fitiavana.accounting.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R

/** Top-of-home-screen block showing equity, cash and emergency fund progress at a glance. */
class HomeMetricsAdapter :
    RecyclerView.Adapter<HomeMetricsAdapter.ViewHolder>() {

    private var metrics = HomeMetrics(
        totalEquity = 0,
        cash = 0,
        emergencyFundPercent = 100,
        cashToEquityPercent = 0,
        monthlyExpenses = 0,
        cashRunwayMonths = 0.0,
        incomeToExpensesPercent = 0
    )

    fun submit(metrics: HomeMetrics) {
        this.metrics = metrics
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_metrics, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(metrics)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val container: LinearLayout =
            view.findViewById(R.id.container_metrics_rows)
        private val context = view.context

        private val equityCash = addPairRow(
            R.string.home_metric_equity_label,
            R.string.home_metric_cash_label
        )
        private val equityView = equityCash.first
        private val cashView = equityCash.second
        private val cashToEquityIncome = addPairRow(
            R.string.home_metric_cash_to_equity_label,
            R.string.home_metric_income_to_expenses_label
        )
        private val cashToEquityView = cashToEquityIncome.first
        private val incomeToExpensesView = cashToEquityIncome.second
        private val monthlyExpenseView =
            addRow(R.string.home_metric_monthly_expense_label)
        private val emergencyFundView =
            addRow(R.string.home_metric_emergency_fund_label)
        private val cashRunwayView =
            addRow(R.string.home_metric_cash_runway_label)

        private fun addRow(@StringRes labelRes: Int): TextView {
            LayoutInflater.from(context)
                .inflate(R.layout.item_home_metric_row, container, true)
            val row =
                container.getChildAt(container.childCount - 1) as LinearLayout
            row.findViewById<TextView>(R.id.text_metric_row_label)
                .setText(labelRes)
            return row.findViewById(R.id.text_metric_row_value)
        }

        /** Two metrics side by side; the right one is right-aligned. */
        private fun addPairRow(
            @StringRes leftLabelRes: Int,
            @StringRes rightLabelRes: Int
        ): Pair<TextView, TextView> {
            LayoutInflater.from(context)
                .inflate(R.layout.item_home_metric_pair_row, container, true)
            val row =
                container.getChildAt(container.childCount - 1) as LinearLayout
            val left = bindCell(row.findViewById(R.id.metric_cell_left), leftLabelRes)
            val right = bindCell(row.findViewById(R.id.metric_cell_right), rightLabelRes)
            return left.second to right.second
        }

        private fun bindCell(
            cell: View,
            @StringRes labelRes: Int
        ): Pair<TextView, TextView> {
            val label = cell.findViewById<TextView>(R.id.text_metric_row_label)
            label.setText(labelRes)
            return label to cell.findViewById(R.id.text_metric_row_value)
        }

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
            incomeToExpensesView.text = context.getString(
                R.string.home_metric_percent_format,
                metrics.incomeToExpensesPercent
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
