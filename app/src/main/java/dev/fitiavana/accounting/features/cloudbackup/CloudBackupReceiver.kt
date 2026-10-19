package dev.fitiavana.accounting.features.cloudbackup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.fitiavana.accounting.AppContainer

/** Fires when the cloud backup alarm goes off, even if the app is not running. */
class CloudBackupReceiver : BroadcastReceiver() {

    /** Swappable so tests can use a fake manager and run the work inline. */
    internal var managerProvider: (Context) -> CloudBackupManager =
        { AppContainer.getInstance(it.applicationContext).cloudBackupManager }
    internal var runInBackground: (() -> Unit) -> Unit = { Thread(it).start() }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CLOUD_BACKUP) return
        // goAsync() keeps the process alive past onReceive while the upload runs off the main thread.
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
        const val ACTION_CLOUD_BACKUP = "dev.fitiavana.accounting.action.CLOUD_BACKUP"
    }
}
