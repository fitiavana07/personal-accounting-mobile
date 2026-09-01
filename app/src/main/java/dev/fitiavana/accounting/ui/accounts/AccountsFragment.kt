package dev.fitiavana.accounting.ui.accounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.AccountTypes

class AccountsFragment : Fragment() {

    private lateinit var viewModel: AccountsViewModel
    private lateinit var adapter: AccountsAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_accounts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initViewModel()
        bindViews(view)
        setupRecyclerView()
        setupTypeTabs(view.findViewById(R.id.tabs_account_type))
        setupHideZeroBalanceCheckbox(view.findViewById(R.id.checkbox_hide_zero_balance))
        observeAccounts()
    }

    private fun initViewModel() {
        val container = AppContainer.getInstance(requireContext())
        viewModel =
            ViewModelProvider(
                this,
                AccountsViewModelFactory(
                    container.accountRepository,
                    container.balanceRepository,
                    container.instrumentRepository
                )
            ).get(AccountsViewModel::class.java)
    }

    private fun bindViews(view: View) {
        emptyView = view.findViewById(R.id.text_empty)
    }

    private fun setupRecyclerView() {
        adapter = AccountsAdapter { account ->
            startActivity(
                EditAccountActivity.editIntent(
                    requireContext(),
                    account.id
                )
            )
        }
        requireView().findViewById<RecyclerView>(R.id.recycler_accounts)
            .apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = this@AccountsFragment.adapter
            }
    }

    private fun observeAccounts() {
        viewModel.accounts.observe(viewLifecycleOwner) { accounts ->
            adapter.submitList(accounts)
            if (accounts.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                val hasFilter = viewModel.typeFilter.value != null ||
                    viewModel.hideZeroBalance.value == true
                emptyView.setText(
                    if (hasFilter) R.string.empty_accounts_filtered
                    else R.string.empty_accounts
                )
            } else {
                emptyView.visibility = View.GONE
            }
        }
    }

    private fun setupTypeTabs(tabLayout: TabLayout) {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.filter_all_types))
        resources.getStringArray(R.array.account_type_display).forEach { label ->
            tabLayout.addTab(tabLayout.newTab().setText(label))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val type =
                    if (tab.position == 0) null else AccountTypes.VALUES.getOrNull(
                        tab.position - 1
                    )
                viewModel.setTypeFilter(type)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupHideZeroBalanceCheckbox(checkbox: CheckBox) {
        checkbox.isChecked = viewModel.hideZeroBalance.value ?: true
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setHideZeroBalance(isChecked)
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