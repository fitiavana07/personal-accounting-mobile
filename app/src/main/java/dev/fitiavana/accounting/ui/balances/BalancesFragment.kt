package dev.fitiavana.accounting.ui.balances

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.db.AppDatabase

class BalancesFragment : Fragment() {

    private lateinit var viewModel: BalancesViewModel
    private lateinit var adapter: BalancesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_balances, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = AppDatabase.getInstance(requireContext())
        val balanceRepo = BalanceRepository(db.accountDao(), db.accountBalanceDao(), db.transactionDao())
        val accountRepo = AccountRepository(db.accountDao())
        val instrumentRepo = InstrumentRepository(db.instrumentDao(), db.accountDao())

        viewModel = ViewModelProvider(this, BalancesViewModelFactory(balanceRepo, accountRepo, instrumentRepo))
            .get(BalancesViewModel::class.java)

        adapter = BalancesAdapter()
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_balances)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val emptyView = view.findViewById<TextView>(R.id.text_empty_balances)

        viewModel.balanceItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            if (items.isEmpty()) {
                recycler.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recycler.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_balances, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_recalculate -> {
                Thread {
                    viewModel.recalculateAll()
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), R.string.balances_recalculated, Toast.LENGTH_SHORT).show()
                    }
                }.start()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
