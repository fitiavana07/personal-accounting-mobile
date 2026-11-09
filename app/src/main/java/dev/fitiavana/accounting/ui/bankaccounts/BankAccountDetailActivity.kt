package dev.fitiavana.accounting.ui.bankaccounts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.bankaccounts.BankAccount
import dev.fitiavana.accounting.ui.common.UiUtils

/**
 * One bank account: its name, balance and balance date on top, its transactions below, a button to add a
 * transaction, and an overflow menu to rename it. Transactions cannot be changed once added.
 */
class BankAccountDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ACCOUNT_ID = "account_id"

        fun intent(context: Context, accountId: String): Intent =
            Intent(context, BankAccountDetailActivity::class.java).putExtra(EXTRA_ACCOUNT_ID, accountId)
    }

    private lateinit var viewModel: BankAccountDetailViewModel
    private var account: BankAccount? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)
        if (accountId == null) {
            finish()
            return
        }
        setContentView(R.layout.activity_bank_account_detail)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_bank_accounts)

        viewModel = ViewModelProvider(
            this,
            BankAccountDetailViewModelFactory(AppContainer.getInstance(this).bankAccountRepository, accountId)
        ).get(BankAccountDetailViewModel::class.java)

        val nameView = findViewById<TextView>(R.id.value_bank_account_name)
        val balanceView = findViewById<TextView>(R.id.value_bank_account_balance)
        val dateView = findViewById<TextView>(R.id.value_bank_account_date)
        viewModel.account.observe(this) { loaded ->
            account = loaded
            if (loaded == null) return@observe
            nameView.text = loaded.name
            balanceView.text = UiUtils.formatAmountAr(this, loaded.balance)
            dateView.text = BankAccountDisplay.balanceDate(this, loaded.balanceDate)
        }

        val adapter = BankAccountTransactionsAdapter()
        val recycler = findViewById<RecyclerView>(R.id.recycler_bank_transactions).apply {
            layoutManager = LinearLayoutManager(this@BankAccountDetailActivity)
            this.adapter = adapter
        }
        val emptyView = findViewById<View>(R.id.text_bank_transactions_empty)
        viewModel.transactions.observe(this) { transactions ->
            adapter.submitList(transactions)
            val hasItems = transactions.isNotEmpty()
            recycler.visibility = if (hasItems) View.VISIBLE else View.GONE
            emptyView.visibility = if (hasItems) View.GONE else View.VISIBLE
        }

        findViewById<View>(R.id.fab_add_bank_transaction).setOnClickListener {
            startActivity(AddBankAccountTransactionActivity.intent(this, accountId))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bank_account_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_edit_bank_account -> {
            account?.let { current ->
                EditBankAccountDialog.show(this, current.name) { name, onDone -> viewModel.rename(name, onDone) }
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
