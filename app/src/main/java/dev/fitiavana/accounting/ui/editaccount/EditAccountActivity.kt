package dev.fitiavana.accounting.ui.editaccount

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.db.AppDatabase

class EditAccountActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "account_id"

        fun addIntent(context: Context): Intent =
            Intent(context, EditAccountActivity::class.java)

        fun editIntent(context: Context, accountId: String): Intent =
            Intent(context, EditAccountActivity::class.java)
                .putExtra(EXTRA_ACCOUNT_ID, accountId)
    }

    private lateinit var viewModel: EditAccountViewModel
    private lateinit var nameInput: EditText
    private var accountId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_account)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val repository = AccountRepository(AppDatabase.getInstance(this).accountDao())
        viewModel = ViewModelProvider(this, EditAccountViewModelFactory(repository))
            .get(EditAccountViewModel::class.java)

        nameInput = findViewById(R.id.input_account_name)
        val saveButton: Button = findViewById(R.id.button_save)

        accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)

        if (accountId != null) {
            title = getString(R.string.title_edit_account)
            Thread {
                val account = viewModel.getAccount(accountId!!)
                runOnUiThread {
                    if (account != null) {
                        nameInput.setText(account.name)
                        nameInput.setSelection(account.name.length)
                    }
                }
            }.start()
        } else {
            title = getString(R.string.title_add_account)
        }

        saveButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                Thread {
                    viewModel.saveAccount(accountId, name)
                    runOnUiThread { finish() }
                }.start()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (accountId != null) {
            menuInflater.inflate(R.menu.menu_edit_account, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_account -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_delete_account_title)
                    .setMessage(R.string.dialog_delete_account_message)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        Thread {
                            val account = viewModel.getAccount(accountId!!)
                            if (account != null) viewModel.deleteAccount(account)
                            runOnUiThread { finish() }
                        }.start()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}