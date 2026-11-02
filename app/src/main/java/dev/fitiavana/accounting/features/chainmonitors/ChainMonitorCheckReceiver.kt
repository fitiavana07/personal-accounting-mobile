package dev.fitiavana.accounting.features.chainmonitors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.fitiavana.accounting.AppContainer

/** Fires when the monitor alarm goes off, even if the app is not running. */
class ChainMonitorCheckReceiver : BroadcastReceiver() {

    /** Swappable so tests can use a fake manager and run the work inline. */
    internal var managerProvider: (Context) -> ChainMonitorCheckManager =
        { AppContainer.getInstance(it.applicationContext).chainMonitorCheckManager }
    internal var runInBackground: (() -> Unit) -> Unit = { Thread(it).start() }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MONITOR_CHECK) return
        // goAsync() keeps the process alive past onReceive while the balances are fetched off the main thread.
        val pending = goAsync()
        val manager = managerProvider(context)
        runInBackground {
            try {
                manager.onAlarm()
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        const val ACTION_MONITOR_CHECK = "dev.fitiavana.accounting.action.CHAIN_MONITOR_CHECK"
    }
}
