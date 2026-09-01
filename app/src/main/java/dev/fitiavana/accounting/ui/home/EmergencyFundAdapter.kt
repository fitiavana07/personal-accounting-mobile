package dev.fitiavana.accounting.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

/** Single-row header (above the pie chart) showing monthly expenses and the 6-month emergency fund target. */
class EmergencyFundAdapter(
    private val onEditClick: () -> Unit
) : RecyclerView.Adapter<EmergencyFundAdapter.ViewHolder>() {

    private var info = EmergencyFundInfo(0, 0, 0, 100, 0)

    fun submit(info: EmergencyFundInfo) {
        this.info = info
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_emergency_fund, parent, false)
        return ViewHolder(view, onEditClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(info)
    }

    class ViewHolder(view: View, onEditClick: () -> Unit) :
        RecyclerView.ViewHolder(view) {
        private val monthlyExpensesView: TextView =
            view.findViewById(R.id.text_monthly_expenses)
        private val amountView: TextView =
            view.findViewById(R.id.text_emergency_fund_6month_amount)
        private val percentView: TextView =
            view.findViewById(R.id.text_emergency_fund_6month_percent)
        private val remainingView: TextView =
            view.findViewById(R.id.text_emergency_fund_6month_remaining)
        private val progress: ProgressBar =
            view.findViewById(R.id.progress_emergency_fund_6month)
        private val editButton: ImageView =
            view.findViewById(R.id.button_edit_monthly_expenses)
        private val context = view.context

        init {
            editButton.setOnClickListener { onEditClick() }
        }

        fun bind(info: EmergencyFundInfo) {
            monthlyExpensesView.text = context.getString(
                R.string.home_monthly_expenses,
                UiUtils.formatAmountAr(context, info.monthlyExpenses)
            )

            amountView.text =
                UiUtils.formatAmountAr(context, info.sixMonthTarget)

            percentView.text = context.getString(
                R.string.home_percent_reached,
                UiUtils.formatAmountAr(context, info.sixMonthReached),
                info.sixMonthPercent
            )
            percentView.setTextColor(
                ContextCompat.getColor(
                    context,
                    percentColorRes(info.sixMonthPercent)
                )
            )

            progress.progress = info.sixMonthPercent

            remainingView.text = if (info.sixMonthRemaining <= 0) {
                context.getString(R.string.home_emergency_fund_goal_reached)
            } else {
                context.getString(
                    R.string.home_emergency_fund_remaining,
                    UiUtils.formatAmountAr(context, info.sixMonthRemaining)
                )
            }
        }

        private fun percentColorRes(percent: Int): Int =
            if (percent >= 100) R.color.gain else R.color.emergency_fund_in_progress
    }
}
