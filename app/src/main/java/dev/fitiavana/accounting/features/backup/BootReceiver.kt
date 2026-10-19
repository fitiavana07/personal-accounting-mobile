package dev.fitiavana.accounting.features.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupManager

/** Alarms are cleared by a reboot or an app update, so re-arm the daily backup alarms (local and cloud) then. */
class BootReceiver : BroadcastReceiver() {

    /** Swappable so tests can use a fake manager. */
    internal var managerProvider: (Context) -> AutoBackupManager =
        { AppContainer.getInstance(it.applicationContext).autoBackupManager }

    internal var cloudManagerProvider: (Context) -> CloudBackupManager =
        { AppContainer.getInstance(it.applicationContext).cloudBackupManager }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        managerProvider(context).ensureScheduled()
        cloudManagerProvider(context).ensureScheduled()
    }
}
