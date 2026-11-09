package dev.fitiavana.accounting.ui.bankaccounts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

/** The bank accounts tracked on their own, with their balances; tap one to see its transactions. */
class BankAccountsActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, BankAccountsActivity::class.java)
    }

    private lateinit var viewModel: BankAccountsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bank_accounts)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_bank_accounts)

        viewModel = ViewModelProvider(
            this,
            BankAccountsViewModelFactory(AppContainer.getInstance(this).bankAccountRepository)
        ).get(BankAccountsViewModel::class.java)

        val adapter = BankAccountsAdapter(
            onClick = { startActivity(BankAccountDetailActivity.intent(this, it.id)) }
        )
        val recycler = findViewById<RecyclerView>(R.id.recycler_bank_accounts).apply {
            layoutManager = LinearLayoutManager(this@BankAccountsActivity)
            this.adapter = adapter
        }
        val emptyView = findViewById<View>(R.id.text_bank_accounts_empty)

        viewModel.accounts.observe(this) { accounts ->
            adapter.submit(accounts)
            val hasItems = accounts.isNotEmpty()
            recycler.visibility = if (hasItems) View.VISIBLE else View.GONE
            emptyView.visibility = if (hasItems) View.GONE else View.VISIBLE
        }

        findViewById<View>(R.id.fab_add_bank_transaction).setOnClickListener {
            startActivity(AddBankAccountTransactionActivity.intent(this))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bank_accounts, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_add_bank_account) return super.onOptionsItemSelected(item)
        EditBankAccountDialog.show(this, null) { name, onDone -> viewModel.create(name, onDone) }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
