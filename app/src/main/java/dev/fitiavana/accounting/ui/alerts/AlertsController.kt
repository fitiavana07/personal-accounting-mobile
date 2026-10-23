package dev.fitiavana.accounting.ui.alerts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R

/** Adding, editing and deleting alerts from an [activity]; shared by the Alerts screen and the position detail. */
class AlertsController(private val activity: AppCompatActivity) {

    val viewModel: AlertsViewModel

    private var choices: List<AccountChoice> = emptyList()

    init {
        val container = AppContainer.getInstance(activity)
        viewModel = ViewModelProvider(
            activity,
            AlertsViewModelFactory(
                container.alertRepository,
                container.accountRepository,
                container.balanceRepository,
                container.instrumentRepository,
                container.alertCheckManager
            )
        ).get(AlertsViewModel::class.java)
        viewModel.accountChoices.observe(activity) { choices = it }
    }

    /** Asks for the notification permission, then opens the new-alert dialog; [preselectAccountId] is the account to start on. */
    fun add(preselectAccountId: String? = null) {
        if (choices.isEmpty()) {
            Toast.makeText(activity, R.string.alerts_no_accounts, Toast.LENGTH_SHORT).show()
        } else {
            requestNotificationPermission()
            showEditor(null, preselectAccountId)
        }
    }

    fun edit(item: AlertItem) = showEditor(item, null)

    fun confirmDelete(item: AlertItem) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_delete_alert_title)
            .setMessage(activity.getString(R.string.dialog_delete_alert_message, item.accountName))
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(item.alert.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditor(existing: AlertItem?, preselectAccountId: String?) {
        EditAlertDialog.show(activity, choices, existing, preselectAccountId) { accountId, type, threshold ->
            viewModel.save(existing?.alert, accountId, type, threshold)
        }
    }

    /** Android 13+ needs the user's consent before any notification; the alerts are useless without it. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1
    }
}
