package dev.fitiavana.accounting.ui.accounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.AccountDao
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.db.AppDatabase

class AccountsFragment : Fragment() {

    private lateinit var viewModel: AccountsViewModel
    private lateinit var adapter: AccountsAdapter

    private val accountTypes = listOf(
        "asset",
        "liability",
        "equity",
        "revenue",
        "expense",
        "drawing",
        "gain",
        "loss"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_accounts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val dao: AccountDao =
            AppDatabase.getInstance(requireContext()).accountDao()
        val repository = AccountRepository(dao)
        viewModel =
            ViewModelProvider(this, AccountsViewModelFactory(repository))
                .get(AccountsViewModel::class.java)

        adapter = AccountsAdapter { account ->
            startActivity(
                EditAccountActivity.editIntent(
                    requireContext(),
                    account.id
                )
            )
        }
        view.findViewById<RecyclerView>(R.id.recycler_accounts).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AccountsFragment.adapter
        }

        val spinnerType = view.findViewById<Spinner>(R.id.spinner_account_type)
        val emptyView = view.findViewById<TextView>(R.id.text_empty)

        setupTypeSpinner(spinnerType)

        viewModel.accounts.observe(viewLifecycleOwner) { accounts ->
            adapter.submitList(accounts)
            if (accounts.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                val hasFilter = viewModel.typeFilter.value != null
                emptyView.setText(
                    if (hasFilter) R.string.empty_accounts_filtered
                    else R.string.empty_accounts
                )
            } else {
                emptyView.visibility = View.GONE
            }
        }
    }

    private fun setupTypeSpinner(spinner: Spinner) {
        val allTypesLabel = getString(R.string.filter_all_types)
        val labels = mutableListOf(allTypesLabel)
        labels.addAll(accountTypes.map { it.replaceFirstChar { c -> c.uppercaseChar() } })

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner_small,
            labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinner.adapter = spinnerAdapter
        spinner.setSelection(0, false)

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val type =
                        if (position == 0) null else accountTypes.getOrNull(
                            position - 1
                        )
                    viewModel.setTypeFilter(type)
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_accounts, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_add_account) {
            startActivity(EditAccountActivity.addIntent(requireContext()))
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}