package dev.fitiavana.accounting.features.cloudbackup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.fitiavana.accounting.R

/**
 * The notification that cloud backups keep failing. Posted from [CloudBackupManager], which may run
 * with no screen open, so tapping it opens the Cloud Backup screen (by class name: features must not
 * import the UI package).
 */
class AndroidCloudBackupNotifier(context: Context) : CloudBackupNotifier {

    private val appContext = context.applicationContext

    override fun notifyFailing() {
        createChannel()
        val manager = NotificationManagerCompat.from(appContext)
        // Without the permission (Android 13+) or with notifications turned off, the screen still shows the status.
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = Intent().setClassName(appContext, SCREEN_CLASS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(appContext, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(appContext.getString(R.string.cloud_backup_notification_title))
            .setContentText(appContext.getString(R.string.cloud_backup_notification_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(appContext.getString(R.string.cloud_backup_notification_text)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun clear() {
        NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID, appContext.getString(R.string.cloud_backup_notification_channel), NotificationManager.IMPORTANCE_DEFAULT
        )
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "cloud_backup"
        private const val NOTIFICATION_ID = 1
        private const val SCREEN_CLASS = "dev.fitiavana.accounting.ui.cloudbackup.CloudBackupActivity"
    }
}
