package dev.fitiavana.accounting.ui.home

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.ReportAdapter

class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var adapter: HomeAdapter
    private lateinit var metricsAdapter: HomeMetricsAdapter
    private lateinit var balanceSheetAdapter: ReportAdapter
    private lateinit var pieChartsAdapter: HomePieChartsAdapter
    private lateinit var emergencyFundAdapter: EmergencyFundAdapter
    private lateinit var noteAdapter: HomeNoteAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val container = AppContainer.getInstance(requireContext())
        val balanceRepo = container.balanceRepository
        val accountRepo = container.accountRepository
        val instrumentRepo = container.instrumentRepository
        val exchangeRateRepo = container.exchangeRateRepository
        val settingsRepo = container.settingsRepository

        viewModel = ViewModelProvider(
            this,
            HomeViewModelFactory(
                balanceRepo,
                accountRepo,
                instrumentRepo,
                exchangeRateRepo,
                settingsRepo
            )
        ).get(HomeViewModel::class.java)

        adapter = HomeAdapter { item ->
            startActivity(
                HomeDetailActivity.intent(
                    requireContext(),
                    item.accountId
                )
            )
        }
        metricsAdapter = HomeMetricsAdapter()
        balanceSheetAdapter = ReportAdapter()
        pieChartsAdapter = HomePieChartsAdapter()
        emergencyFundAdapter =
            EmergencyFundAdapter { showEditMonthlyExpensesDialog() }
        noteAdapter = HomeNoteAdapter()
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_home)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter =
            ConcatAdapter(
                metricsAdapter,
                emergencyFundAdapter,
                pieChartsAdapter,
                balanceSheetAdapter,
                noteAdapter,
                adapter
            )

        val emptyView = view.findViewById<TextView>(R.id.text_empty_home)
        swipeRefresh = view.findViewById(R.id.swipe_refresh_home)
        swipeRefresh.setOnRefreshListener { refreshRates() }

        fun updateEmptyState() {
            val isEmpty =
                balanceSheetAdapter.itemCount == 0 && adapter.itemCount == 0
            recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
            emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }

        viewModel.balanceSheetRows.observe(viewLifecycleOwner) { rows ->
            balanceSheetAdapter.submitList(rows)
            updateEmptyState()
        }

        viewModel.homeItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            noteAdapter.setVisible(items.any { it.instrument.type == "stock" })
            updateEmptyState()
        }

        viewModel.assetSlices.observe(viewLifecycleOwner) { slices ->
            pieChartsAdapter.submitAssetSlices(slices)
        }

        viewModel.liquiditySlices.observe(viewLifecycleOwner) { slices ->
            pieChartsAdapter.submitLiquiditySlices(slices)
        }

        viewModel.emergencyFund.observe(viewLifecycleOwner) { info ->
            emergencyFundAdapter.submit(info)
        }

        viewModel.metrics.observe(viewLifecycleOwner) { metrics ->
            metricsAdapter.submit(metrics)
        }

        refreshRates()
    }

    private fun showEditMonthlyExpensesDialog() {
        val currentValue = viewModel.emergencyFund.value?.monthlyExpenses ?: 0L
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_monthly_expenses, null)
        val input =
            dialogView.findViewById<EditText>(R.id.input_monthly_expenses)
                .apply {
                    setText(currentValue.toString())
                    setSelection(text.length)
                }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_edit_monthly_expenses_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val amount = input.text.toString().trim().toLongOrNull()
                if (amount != null) {
                    Thread { viewModel.setMonthlyLivingExpenses(amount) }.start()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshRates() {
        swipeRefresh.isRefreshing = true
        Thread {
            val result = viewModel.refreshRates()
            activity?.runOnUiThread {
                swipeRefresh.isRefreshing = false
                if (result.failed.isNotEmpty() && result.succeeded.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        R.string.home_refresh_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }
}
