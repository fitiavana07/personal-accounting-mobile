package dev.fitiavana.accounting.features.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.fitiavana.accounting.AppContainer

/** Fires when the hourly alarm goes off, even if the app is not running. */
class AlertCheckReceiver : BroadcastReceiver() {

    /** Swappable so tests can use a fake manager and run the work inline. */
    internal var managerProvider: (Context) -> AlertCheckManager =
        { AppContainer.getInstance(it.applicationContext).alertCheckManager }
    internal var runInBackground: (() -> Unit) -> Unit = { Thread(it).start() }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALERT_CHECK) return
        // goAsync() keeps the process alive past onReceive while the rates are fetched off the main thread.
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
        const val ACTION_ALERT_CHECK = "dev.fitiavana.accounting.action.ALERT_CHECK"
    }
}
