package dev.fitiavana.accounting.ui.accounts

import android.os.Bundle
import android.view.LayoutInflater
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

class AccountsFragment : Fragment() {

    private lateinit var viewModel: AccountsViewModel
    private lateinit var adapter: AccountsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_accounts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val dao: AccountDao = AppDatabase.getInstance(requireContext()).accountDao()
        val repository = AccountRepository(dao)
        viewModel = ViewModelProvider(this, AccountsViewModelFactory(repository))
            .get(AccountsViewModel::class.java)

        adapter = AccountsAdapter()
        view.findViewById<RecyclerView>(R.id.recycler_accounts).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AccountsFragment.adapter
        }

        viewModel.accounts.observe(viewLifecycleOwner) { accounts ->
            adapter.submitList(accounts)
        }
    }
}
