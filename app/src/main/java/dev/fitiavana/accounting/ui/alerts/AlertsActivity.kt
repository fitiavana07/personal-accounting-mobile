package dev.fitiavana.accounting.ui.alerts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

/** The gain/loss alerts: notifications when an account's gain/loss % crosses a threshold. */
class AlertsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1

        fun intent(context: Context): Intent = Intent(context, AlertsActivity::class.java)
    }

    private var choices: List<AccountChoice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_alerts)

        val container = AppContainer.getInstance(this)
        val viewModel = ViewModelProvider(
            this,
            AlertsViewModelFactory(
                container.alertRepository,
                container.accountRepository,
                container.balanceRepository,
                container.instrumentRepository,
                container.alertCheckManager
            )
        ).get(AlertsViewModel::class.java)

        val adapter = AlertsAdapter(
            onClick = { item -> showEditor(viewModel, item) },
            onDelete = { item -> confirmDelete(viewModel, item) }
        )
        val recycler = findViewById<RecyclerView>(R.id.recycler_alerts).apply {
            layoutManager = LinearLayoutManager(this@AlertsActivity)
            this.adapter = adapter
        }
        val emptyView = findViewById<View>(R.id.text_alerts_empty)

        viewModel.items.observe(this) { items ->
            adapter.submit(items)
            val hasItems = items.isNotEmpty()
            recycler.visibility = if (hasItems) View.VISIBLE else View.GONE
            emptyView.visibility = if (hasItems) View.GONE else View.VISIBLE
        }
        viewModel.accountChoices.observe(this) { choices = it }

        findViewById<View>(R.id.button_add_alert).setOnClickListener {
            if (choices.isEmpty()) {
                Toast.makeText(this, R.string.alerts_no_accounts, Toast.LENGTH_SHORT).show()
            } else {
                requestNotificationPermission()
                showEditor(viewModel, null)
            }
        }
    }

    private fun showEditor(viewModel: AlertsViewModel, existing: AlertItem?) {
        EditAlertDialog.show(this, choices, existing) { accountId, type, threshold ->
            viewModel.save(existing?.alert, accountId, type, threshold)
        }
    }

    private fun confirmDelete(viewModel: AlertsViewModel, item: AlertItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_alert_title)
            .setMessage(getString(R.string.dialog_delete_alert_message, item.accountName))
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(item.alert.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Android 13+ needs the user's consent before any notification; the alerts are useless without it. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
