package dev.fitiavana.accounting.ui.alerts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.fitiavana.accounting.R

/**
 * Android 13+ needs the user's consent before any notification; the alerts are useless without it.
 * Create it while the [activity] is being created (it registers a result launcher).
 */
class NotificationPermission(private val activity: AppCompatActivity) {

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Denied without the system dialog having a say (turned off in Settings, or refused twice): only Settings can help.
        if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
            showBlockedDialog()
        }
    }

    /** Asks for the permission unless it is already granted (or not needed below Android 13). */
    fun request() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showBlockedDialog() {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_notifications_off_title)
            .setMessage(R.string.dialog_notifications_off_message)
            .setPositiveButton(R.string.action_open_settings) { _, _ ->
                activity.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
