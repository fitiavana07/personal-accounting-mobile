package dev.fitiavana.accounting.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Typeface
import android.widget.ArrayAdapter
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.EditAprDialog
import dev.fitiavana.accounting.ui.common.EquityStatementAdapter
import dev.fitiavana.accounting.ui.common.ReportAdapter
import dev.fitiavana.accounting.ui.common.UiUtils

class ReportsFragment : Fragment() {

    private var resumedBefore = false

    private lateinit var viewModel: ReportsViewModel
    private lateinit var contentAdapter: ReportAdapter
    private lateinit var equityAdapter: EquityStatementAdapter
    private lateinit var monthsAdapter: PeriodSelectorAdapter<Int?>
    private lateinit var reportTypeAdapter: PeriodSelectorAdapter<ReportType>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_reports, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = AppContainer.getInstance(requireContext())
        val accountRepo = container.accountRepository
        val balanceRepo = container.balanceRepository
        val instrumentRepo = container.instrumentRepository

        viewModel = ViewModelProvider(
            this,
            ReportsViewModelFactory(accountRepo, balanceRepo, instrumentRepo, container.trendVisibilityStore)
        )
            .get(ReportsViewModel::class.java)

        monthsAdapter = PeriodSelectorAdapter(
            labelFor = { it?.let { month -> ReportPeriodSelector.shortMonthName(month) } ?: "Year" },
            onSelected = { viewModel.selectMonth(it) },
            layoutRes = R.layout.item_period_compact
        )
        reportTypeAdapter = PeriodSelectorAdapter(
            labelFor = { it.shortLabel },
            onSelected = { viewModel.selectReportType(it) },
            iconFor = { it.iconRes },
            layoutRes = R.layout.item_period_segment
        )
        contentAdapter = ReportAdapter(
            onAccountClick = { viewModel.toggleAccount(it) },
            onAprClick = { line ->
                EditAprDialog.show(requireContext(), line.accountName, line.aprPercent) {
                    line.accountId?.let { id -> viewModel.updateApr(id, it) }
                }
            },
            cardStyle = true
        )
        equityAdapter = EquityStatementAdapter()

        val yearView = view.findViewById<TextView>(R.id.text_reports_year)
        yearView.setOnClickListener { showYearDropdown(yearView) }
        view.findViewById<RecyclerView>(R.id.recycler_reports_months).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = monthsAdapter
        }
        view.findViewById<RecyclerView>(R.id.recycler_reports_type).apply {
            // Short labels keep all three report types visible without scrolling.
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = reportTypeAdapter
        }
        view.findViewById<RecyclerView>(R.id.recycler_reports_content).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contentAdapter
        }
        view.findViewById<RecyclerView>(R.id.recycler_reports_equity).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = equityAdapter
        }

        val contentLayout = view.findViewById<View>(R.id.layout_reports_content)
        val emptyView = view.findViewById<TextView>(R.id.text_empty_reports)
        val asOfDateView = view.findViewById<TextView>(R.id.text_reports_as_of_date)
        val expandAllView = view.findViewById<TextView>(R.id.text_reports_expand_all)
        expandAllView.setOnClickListener { viewModel.toggleExpandAll() }
        val contentRecycler = view.findViewById<RecyclerView>(R.id.recycler_reports_content)
        val equityScroll = view.findViewById<View>(R.id.scroll_reports_equity)

        setUpTrendCharts(view)

        reportTypeAdapter.submitList(viewModel.reportTypes, viewModel.selectedReportType.value)

        viewModel.hasTransactions.observe(viewLifecycleOwner) { hasTransactions ->
            contentLayout.visibility = if (hasTransactions) View.VISIBLE else View.GONE
            emptyView.visibility = if (hasTransactions) View.GONE else View.VISIBLE
        }
        viewModel.selectedYear.observe(viewLifecycleOwner) { yearView.text = "$it $YEAR_CHEVRON" }
        viewModel.availableMonths.observe(viewLifecycleOwner) { monthsAdapter.submitList(it, viewModel.selectedMonth.value) }
        viewModel.selectedMonth.observe(viewLifecycleOwner) { monthsAdapter.submitList(viewModel.availableMonths.value ?: emptyList(), it) }
        viewModel.selectedReportType.observe(viewLifecycleOwner) { reportType ->
            reportTypeAdapter.submitList(viewModel.reportTypes, reportType)
            val isEquityStatement = reportType == ReportType.CHANGES_IN_EQUITY
            contentRecycler.visibility = if (isEquityStatement) View.GONE else View.VISIBLE
            equityScroll.visibility = if (isEquityStatement) View.VISIBLE else View.GONE
        }
        viewModel.expandToggle.observe(viewLifecycleOwner) { state ->
            expandAllView.visibility = if (state.visible) View.VISIBLE else View.GONE
            expandAllView.setText(if (state.allExpanded) R.string.collapse_all else R.string.expand_all)
        }
        viewModel.asOfDateText.observe(viewLifecycleOwner) { asOfDateView.text = it }
        viewModel.balanceSheetRows.observe(viewLifecycleOwner) { contentAdapter.submitList(it) }
        viewModel.equityStatement.observe(viewLifecycleOwner) { equityAdapter.submitList(it) }

        viewModel.start()
    }

    /** The years in a dropdown right below the chip (like a spinner), the selected one in bold gold. */
    private fun showYearDropdown(anchor: View) {
        val years = viewModel.availableYears.value.orEmpty()
        if (years.isEmpty()) return
        val selected = viewModel.selectedYear.value
        val context = requireContext()
        val gold = ContextCompat.getColor(context, R.color.gold_500)
        val adapter = object : ArrayAdapter<Int>(context, android.R.layout.simple_list_item_1, years) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    val isSelected = getItem(position) == selected
                    setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                    if (isSelected) setTextColor(gold)
                }
        }
        ListPopupWindow(context).apply {
            anchorView = anchor
            setAdapter(adapter)
            width = (DROPDOWN_MIN_WIDTH_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(anchor.width)
            isModal = true
            setOnItemClickListener { _, _, position, _ ->
                viewModel.selectYear(years[position])
                dismiss()
            }
        }.show()
    }

    /** Reloads the reports on returning from another screen (e.g. Add Transaction); the first resume follows the initial load. */
    override fun onResume() {
        super.onResume()
        if (resumedBefore) viewModel.refresh()
        resumedBefore = true
    }

    /**
     * Net worth and net income charts above the report, collapsible (the choice is remembered). Tapping a month
     * selects that period; the selected month is highlighted and its values are shown next to each chart title.
     */
    private fun setUpTrendCharts(view: View) {
        val trendBlock = view.findViewById<View>(R.id.layout_trend)
        val toggleView = view.findViewById<TextView>(R.id.text_trend_toggle)
        val chartsBlock = view.findViewById<View>(R.id.layout_trend_charts)
        val progress = view.findViewById<View>(R.id.progress_trend)
        val netWorthChart = view.findViewById<LineChart>(R.id.chart_net_worth)
        val netIncomeChart = view.findViewById<BarChart>(R.id.chart_net_income)
        val netWorthValue = view.findViewById<TextView>(R.id.text_net_worth_value)
        val netIncomeValue = view.findViewById<TextView>(R.id.text_net_income_value)
        TrendChartRenderer.configureLine(netWorthChart)
        TrendChartRenderer.configureBar(netIncomeChart)
        toggleView.setOnClickListener { viewModel.toggleTrendVisible() }

        fun selectedYearMonth(): YearMonth? {
            val year = viewModel.selectedYear.value ?: return null
            val month = viewModel.selectedMonth.value ?: return null
            return YearMonth(year, month)
        }

        fun refreshSelection() {
            val points = viewModel.trendPoints.value ?: return
            if (!TrendChartRenderer.hasEnoughData(points)) return
            val selected = selectedYearMonth()
            TrendChartRenderer.updateSelection(netWorthChart, points, selected)
            TrendChartRenderer.updateSelection(netIncomeChart, points, selected)
            val point = points.firstOrNull { it.yearMonth == selected }
            fun valueText(amount: Long?) = amount?.let {
                "${ReportPeriodSelector.shortMonthName(point!!.yearMonth.month)}  ${UiUtils.formatAmountAr(requireContext(), it)}"
            }.orEmpty()
            netWorthValue.text = valueText(point?.netWorth)
            netIncomeValue.text = valueText(point?.netIncome)
        }

        viewModel.trendVisible.observe(viewLifecycleOwner) { visible ->
            trendBlock.visibility = if (visible) View.VISIBLE else View.GONE
            toggleView.text = "${getString(R.string.trend_toggle_title)} ${if (visible) CHEVRON_EXPANDED else CHEVRON_COLLAPSED}"
        }
        viewModel.trendPoints.observe(viewLifecycleOwner) { points ->
            chartsBlock.visibility = TrendChartRenderer.blockVisibility(points)
            progress.visibility = TrendChartRenderer.loadingVisibility(points)
            toggleView.visibility = if (points != null && !TrendChartRenderer.hasEnoughData(points)) View.GONE else View.VISIBLE
            if (points != null && TrendChartRenderer.hasEnoughData(points)) {
                val onMonthSelected = { month: YearMonth -> viewModel.selectPeriod(month.year, month.month) }
                TrendChartRenderer.renderNetWorth(netWorthChart, points, selectedYearMonth(), onMonthSelected)
                TrendChartRenderer.renderNetIncome(netIncomeChart, points, selectedYearMonth(), onMonthSelected)
                refreshSelection()
            }
        }
        viewModel.selectedYear.observe(viewLifecycleOwner) { refreshSelection() }
        viewModel.selectedMonth.observe(viewLifecycleOwner) { refreshSelection() }
    }

    private companion object {
        // ▼ (U+25BC) renders on API 19's system font, unlike ▾; ► and ▼ match the Balance Sheet's chevrons.
        const val YEAR_CHEVRON = "▼"
        const val CHEVRON_COLLAPSED = "►"
        const val CHEVRON_EXPANDED = "▼"
        const val DROPDOWN_MIN_WIDTH_DP = 112
    }
}
