package dev.fitiavana.accounting.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

/** Single-row section showing average monthly net income (last 6 full months) against monthly expenses. */
class IncomeToExpensesAdapter :
    RecyclerView.Adapter<IncomeToExpensesAdapter.ViewHolder>() {

    private var info = IncomeToExpensesInfo(0, 0, 0, 0)

    fun submit(info: IncomeToExpensesInfo) {
        this.info = info
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_income_to_expenses, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(info)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val amountView: TextView =
            view.findViewById(R.id.text_income_to_expenses_amount)
        private val percentView: TextView =
            view.findViewById(R.id.text_income_to_expenses_percent)
        private val remainingView: TextView =
            view.findViewById(R.id.text_income_to_expenses_remaining)
        private val progress: ProgressBar =
            view.findViewById(R.id.progress_income_to_expenses)
        private val context = view.context

        fun bind(info: IncomeToExpensesInfo) {
            amountView.text =
                UiUtils.formatAmountAr(context, info.monthlyExpenses)

            percentView.text = context.getString(
                R.string.home_progress_percent_reached,
                UiUtils.formatAmountAr(context, info.averageMonthlyIncome),
                info.percent
            )
            percentView.setTextColor(
                ContextCompat.getColor(
                    context,
                    UiUtils.progressPercentColorRes(info.percent)
                )
            )

            progress.progress = info.percent

            remainingView.text = if (info.remaining <= 0) {
                context.getString(R.string.home_progress_goal_reached)
            } else {
                context.getString(
                    R.string.home_progress_remaining,
                    UiUtils.formatAmountAr(context, info.remaining)
                )
            }
        }
    }
}
