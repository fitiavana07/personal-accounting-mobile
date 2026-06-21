package dev.fitiavana.accounting.ui.transactions

import android.app.DatePickerDialog
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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dev.fitiavana.accounting.BuildConfig
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.TransactionRepository
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.ui.addtransaction.AddTransactionActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TransactionsFragment : Fragment() {

    private lateinit var viewModel: TransactionsViewModel
    private lateinit var adapter: TransactionsAdapter

    private val dateFormat =
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_transactions_debug, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_clear_transactions) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_clear_transactions_title)
                .setMessage(R.string.dialog_clear_transactions_message)
                .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.clearAllTransactions() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        inflater.inflate(R.layout.fragment_transactions, container, false)

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

        val fab =
            view.findViewById<FloatingActionButton>(R.id.fab_add_transaction)
        val emptyView = view.findViewById<TextView>(R.id.text_empty)
        val spinnerAccount = view.findViewById<Spinner>(R.id.spinner_account)
        val textDateStart = view.findViewById<TextView>(R.id.text_date_start)
        val textDateEnd = view.findViewById<TextView>(R.id.text_date_end)

        fab.setOnClickListener {
            startActivity(AddTransactionActivity.intent(requireContext()))
        }

        // Initialize date labels from default filter
        val defaultFilter =
            viewModel.filter.value ?: TransactionsViewModel.defaultFilter()
        textDateStart.text = dateFormat.format(defaultFilter.startMs)
        textDateEnd.text = dateFormat.format(defaultFilter.endMs)

        textDateStart.setOnClickListener {
            val current =
                viewModel.filter.value ?: TransactionsViewModel.defaultFilter()
            val cal =
                Calendar.getInstance().apply { timeInMillis = current.startMs }
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val newCal = Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    textDateStart.text = dateFormat.format(newCal.time)
                    viewModel.setDateFilter(newCal.timeInMillis, current.endMs)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        textDateEnd.setOnClickListener {
            val current =
                viewModel.filter.value ?: TransactionsViewModel.defaultFilter()
            val cal =
                Calendar.getInstance().apply { timeInMillis = current.endMs }
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val newCal = Calendar.getInstance().apply {
                        set(year, month, day, 23, 59, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    textDateEnd.text = dateFormat.format(newCal.time)
                    viewModel.setDateFilter(
                        current.startMs,
                        newCal.timeInMillis
                    )
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        viewModel.accounts.observe(viewLifecycleOwner) { accounts ->
            setupAccountSpinner(spinnerAccount, accounts)
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
                val hasActiveFilter = viewModel.filter.value?.accountId != null
                emptyView.setText(
                    if (hasActiveFilter) R.string.empty_transactions_filtered
                    else R.string.empty_transactions
                )
            } else {
                emptyView.visibility = View.GONE
            }

            val items = transactions.map { twe ->
                TransactionDisplayItem(
                    twe.transaction,
                    twe.entries,
                    accountsMap
                )
            }
            adapter.submitList(items)
        }
    }

    private fun setupAccountSpinner(
        spinner: Spinner,
        accounts: List<Account>
    ) {
        val allAccountsLabel = getString(R.string.filter_all_accounts)
        val labels = mutableListOf(allAccountsLabel)
        labels.addAll(accounts.map { it.name })

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val currentAccountId = viewModel.filter.value?.accountId
        val selectedPosition = if (currentAccountId == null) 0
        else accounts.indexOfFirst { it.id == currentAccountId }
            .let { if (it < 0) 0 else it + 1 }

        spinner.adapter = spinnerAdapter
        spinner.setSelection(selectedPosition, false)

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val accountId =
                        if (position == 0) null else accounts.getOrNull(
                            position - 1
                        )?.id
                    viewModel.setAccountFilter(accountId)
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }
}