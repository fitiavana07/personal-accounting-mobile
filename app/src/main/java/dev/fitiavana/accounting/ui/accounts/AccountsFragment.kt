package dev.fitiavana.accounting.ui.accounts

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.dao.AccountDao
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.ui.editaccount.EditAccountActivity

class AccountsFragment : Fragment() {

    private lateinit var viewModel: AccountsViewModel
    private lateinit var adapter: AccountsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_accounts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val dao: AccountDao = AppDatabase.getInstance(requireContext()).accountDao()
        val repository = AccountRepository(dao)
        viewModel = ViewModelProvider(this, AccountsViewModelFactory(repository))
            .get(AccountsViewModel::class.java)

        adapter = AccountsAdapter { account ->
            startActivity(EditAccountActivity.editIntent(requireContext(), account.id))
        }
        view.findViewById<RecyclerView>(R.id.recycler_accounts).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AccountsFragment.adapter
        }

        viewModel.accounts.observe(viewLifecycleOwner) { accounts ->
            adapter.submitList(accounts)
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