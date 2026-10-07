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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.cexprices.CexPricesActivity
import dev.fitiavana.accounting.ui.common.ReportAdapter
import dev.fitiavana.accounting.ui.transactions.AddTransactionActivity

class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var adapter: HomeAdapter
    private lateinit var metricsAdapter: HomeMetricsAdapter
    private lateinit var balanceSheetAdapter: ReportAdapter
    private lateinit var pieChartsAdapter: HomePieChartsAdapter
    private lateinit var shortcutsAdapter: HomeShortcutsAdapter
    private lateinit var p2pPricesAdapter: HomeP2pPricesAdapter
    private lateinit var emergencyFundAdapter: EmergencyFundAdapter
    private lateinit var incomeToExpensesAdapter: IncomeToExpensesAdapter
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
                settingsRepo,
                container.p2pPriceRepository
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
        shortcutsAdapter = HomeShortcutsAdapter(
            onCexPricesClick = {
                startActivity(CexPricesActivity.intent(requireContext()))
            },
            onMonthlyExpensesClick = { showEditMonthlyExpensesDialog() }
        )
        p2pPricesAdapter = HomeP2pPricesAdapter { showP2pFilterDialog() }
        emergencyFundAdapter = EmergencyFundAdapter()
        incomeToExpensesAdapter = IncomeToExpensesAdapter()
        noteAdapter = HomeNoteAdapter()
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_home)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter =
            ConcatAdapter(
                metricsAdapter,
                shortcutsAdapter,
                p2pPricesAdapter,
                emergencyFundAdapter,
                incomeToExpensesAdapter,
                pieChartsAdapter,
                balanceSheetAdapter,
                noteAdapter,
                adapter
            )

        val emptyView = view.findViewById<TextView>(R.id.text_empty_home)
        swipeRefresh = view.findViewById(R.id.swipe_refresh_home)
        swipeRefresh.setOnRefreshListener { refreshRates() }

        view.findViewById<FloatingActionButton>(R.id.fab_add_transaction)
            .setOnClickListener {
                startActivity(AddTransactionActivity.intent(requireContext()))
            }

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

        viewModel.p2pPrices.observe(viewLifecycleOwner) { prices ->
            p2pPricesAdapter.submit(prices)
        }

        viewModel.emergencyFund.observe(viewLifecycleOwner) { info ->
            emergencyFundAdapter.submit(info)
        }

        viewModel.incomeToExpenses.observe(viewLifecycleOwner) { info ->
            incomeToExpensesAdapter.submit(info)
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

    /** Single-choice dialog over "All methods" + the payment methods Binance offers; choosing one refreshes the prices. */
    private fun showP2pFilterDialog() {
        Thread {
            val methods = viewModel.getPaymentMethods()
            val selected = viewModel.getSelectedPaymentMethod()
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (methods.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.home_p2p_filter_unavailable, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val items = (listOf(getString(R.string.home_p2p_filter_all)) + methods.map { it.name }).toTypedArray()
                val checked = methods.indexOfFirst { it.identifier == selected } + 1
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.home_p2p_filter_dialog_title)
                    .setSingleChoiceItems(items, checked) { dialog, which ->
                        dialog.dismiss()
                        val identifier = methods.getOrNull(which - 1)?.identifier
                        Thread { viewModel.selectPaymentMethod(identifier) }.start()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }.start()
    }

    private fun refreshRates() {
        swipeRefresh.isRefreshing = true
        val p2pRefresh = Thread { viewModel.refreshP2pPrices() }.apply { start() }
        Thread {
            val result = viewModel.refreshRates()
            p2pRefresh.join()
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
