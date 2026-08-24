package dev.fitiavana.accounting.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var adapter: HomeAdapter
    private lateinit var balanceSheetAdapter: BalanceSheetAdapter
    private lateinit var assetsPieChartAdapter: AssetsPieChartAdapter
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

        viewModel = ViewModelProvider(
            this,
            HomeViewModelFactory(balanceRepo, accountRepo, instrumentRepo, exchangeRateRepo)
        ).get(HomeViewModel::class.java)

        adapter = HomeAdapter { item ->
            startActivity(HomeDetailActivity.intent(requireContext(), item.accountId))
        }
        balanceSheetAdapter = BalanceSheetAdapter()
        assetsPieChartAdapter = AssetsPieChartAdapter()
        noteAdapter = HomeNoteAdapter()
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_home)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = ConcatAdapter(assetsPieChartAdapter, balanceSheetAdapter, noteAdapter, adapter)

        val emptyView = view.findViewById<TextView>(R.id.text_empty_home)
        swipeRefresh = view.findViewById(R.id.swipe_refresh_home)
        swipeRefresh.setOnRefreshListener { refreshRates() }

        fun updateEmptyState() {
            val isEmpty = balanceSheetAdapter.itemCount == 0 && adapter.itemCount == 0
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
            assetsPieChartAdapter.submitList(slices)
        }

        refreshRates()
    }

    private fun refreshRates() {
        swipeRefresh.isRefreshing = true
        Thread {
            val result = viewModel.refreshRates()
            activity?.runOnUiThread {
                swipeRefresh.isRefreshing = false
                if (result.failed.isNotEmpty() && result.succeeded.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.home_refresh_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
