package dev.fitiavana.accounting.ui.chainmonitors

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitor
import dev.fitiavana.accounting.ui.alerts.NotificationPermission

/** Adding, editing and deleting chain monitors from an [activity]. */
class ChainMonitorsController(private val activity: AppCompatActivity) {

    val viewModel: ChainMonitorsViewModel

    private val notificationPermission = NotificationPermission(activity)

    init {
        val container = AppContainer.getInstance(activity)
        viewModel = ViewModelProvider(
            activity,
            ChainMonitorsViewModelFactory(
                container.chainMonitorRepository,
                container.erc20BalanceFetcher,
                container.chainMonitorCheckManager
            )
        ).get(ChainMonitorsViewModel::class.java)
    }

    /** Asks for the notification permission, then opens the new-monitor dialog. */
    fun add() {
        notificationPermission.request()
        showEditor(null)
    }

    fun edit(monitor: ChainMonitor) = showEditor(monitor)

    fun confirmDelete(monitor: ChainMonitor) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_delete_chain_monitor_title)
            .setMessage(activity.getString(R.string.dialog_delete_chain_monitor_message, monitor.label))
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(monitor.id) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditor(existing: ChainMonitor?) {
        EditChainMonitorDialog.show(activity, existing) { form, onDone -> viewModel.save(existing, form, onDone) }
    }
}
