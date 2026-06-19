package dev.fitiavana.accounting.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.TransactionRepository
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.ui.addtransaction.AddTransactionActivity

class TransactionsFragment : Fragment() {

    private lateinit var viewModel: TransactionsViewModel
    private lateinit var adapter: TransactionsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_transactions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val db = AppDatabase.getInstance(requireContext())
        val transactionRepo = TransactionRepository(db.transactionDao())
        val accountRepo = AccountRepository(db.accountDao())
        viewModel = ViewModelProvider(
            this,
            TransactionsViewModelFactory(transactionRepo, accountRepo)
        ).get(TransactionsViewModel::class.java)

        adapter = TransactionsAdapter { item ->
            startActivity(
                dev.fitiavana.accounting.ui.transactiondetail.TransactionDetailActivity
                    .intent(requireContext(), item.transaction.id)
            )
        }
        view.findViewById<RecyclerView>(R.id.recycler_transactions).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TransactionsFragment.adapter
        }

        val fab = view.findViewById<FloatingActionButton>(R.id.fab_add_transaction)
        val emptyView = view.findViewById<TextView>(R.id.text_empty)

        fab.setOnClickListener {
            startActivity(AddTransactionActivity.intent(requireContext()))
        }

        viewModel.combined.observe(viewLifecycleOwner) { (transactions, accounts) ->
            val accountsMap = accounts.associate { it.id to it.name }
            val hasAccounts = accounts.isNotEmpty()

            if (!hasAccounts) {
                fab.hide()
                emptyView.visibility = View.VISIBLE
                emptyView.setText(R.string.empty_transactions_no_accounts)
                adapter.submitList(emptyList())
                return@observe
            }

            fab.show()

            if (transactions.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                emptyView.setText(R.string.empty_transactions)
            } else {
                emptyView.visibility = View.GONE
            }

            val items = transactions.map { twe ->
                TransactionDisplayItem(twe.transaction, twe.entries, accountsMap)
            }
            adapter.submitList(items)
        }
    }
}
