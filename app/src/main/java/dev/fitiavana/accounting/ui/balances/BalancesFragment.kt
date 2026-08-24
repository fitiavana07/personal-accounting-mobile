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
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R

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
        val container = AppContainer.getInstance(requireContext())
        val balanceRepo = container.balanceRepository
        val accountRepo = container.accountRepository
        val instrumentRepo = container.instrumentRepository

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
