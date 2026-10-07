package dev.fitiavana.accounting.features.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.fitiavana.accounting.AppContainer

/** Fires when the daily alarm goes off, even if the app is not running. */
class AutoBackupReceiver : BroadcastReceiver() {

    /** Swappable so tests can use a fake manager and run the work inline. */
    internal var managerProvider: (Context) -> AutoBackupManager =
        { AppContainer.getInstance(it.applicationContext).autoBackupManager }
    internal var runInBackground: (() -> Unit) -> Unit = { Thread(it).start() }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AUTO_BACKUP) return
        // goAsync() keeps the process alive past onReceive while the backup runs off the main thread.
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
        const val ACTION_AUTO_BACKUP = "dev.fitiavana.accounting.action.AUTO_BACKUP"
    }
}
