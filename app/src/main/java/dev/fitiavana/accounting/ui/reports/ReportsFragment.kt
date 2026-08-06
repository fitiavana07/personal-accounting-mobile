package dev.fitiavana.accounting.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.BalanceRepository
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.ui.home.BalanceSheetAdapter

class ReportsFragment : Fragment() {

    private lateinit var viewModel: ReportsViewModel
    private lateinit var contentAdapter: BalanceSheetAdapter
    private lateinit var yearsAdapter: PeriodSelectorAdapter
    private lateinit var monthsAdapter: PeriodSelectorAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_reports, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = AppDatabase.getInstance(requireContext())
        val accountRepo = AccountRepository(db.accountDao())
        val balanceRepo = BalanceRepository(db.accountDao(), db.accountBalanceDao(), db.transactionDao())

        viewModel = ViewModelProvider(this, ReportsViewModelFactory(accountRepo, balanceRepo))
            .get(ReportsViewModel::class.java)

        yearsAdapter = PeriodSelectorAdapter(labelFor = { it.toString() }, onSelected = { viewModel.selectYear(it) })
        monthsAdapter = PeriodSelectorAdapter(labelFor = { ReportPeriodSelector.monthName(it) }, onSelected = { viewModel.selectMonth(it) })
        contentAdapter = BalanceSheetAdapter()

        view.findViewById<RecyclerView>(R.id.recycler_reports_years).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = yearsAdapter
        }
        view.findViewById<RecyclerView>(R.id.recycler_reports_months).apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = monthsAdapter
        }
        view.findViewById<RecyclerView>(R.id.recycler_reports_content).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = contentAdapter
        }

        val contentLayout = view.findViewById<View>(R.id.layout_reports_content)
        val emptyView = view.findViewById<TextView>(R.id.text_empty_reports)
        val asOfDateView = view.findViewById<TextView>(R.id.text_reports_as_of_date)

        viewModel.hasTransactions.observe(viewLifecycleOwner) { hasTransactions ->
            contentLayout.visibility = if (hasTransactions) View.VISIBLE else View.GONE
            emptyView.visibility = if (hasTransactions) View.GONE else View.VISIBLE
        }
        viewModel.availableYears.observe(viewLifecycleOwner) { yearsAdapter.submitList(it, viewModel.selectedYear.value) }
        viewModel.selectedYear.observe(viewLifecycleOwner) { yearsAdapter.submitList(viewModel.availableYears.value ?: emptyList(), it) }
        viewModel.availableMonths.observe(viewLifecycleOwner) { monthsAdapter.submitList(it, viewModel.selectedMonth.value) }
        viewModel.selectedMonth.observe(viewLifecycleOwner) { monthsAdapter.submitList(viewModel.availableMonths.value ?: emptyList(), it) }
        viewModel.asOfDateText.observe(viewLifecycleOwner) { asOfDateView.text = it }
        viewModel.balanceSheetRows.observe(viewLifecycleOwner) { contentAdapter.submitList(it) }

        viewModel.start()
    }
}
