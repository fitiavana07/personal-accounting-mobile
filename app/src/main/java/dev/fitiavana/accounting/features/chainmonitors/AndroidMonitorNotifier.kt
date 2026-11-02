package dev.fitiavana.accounting.features.chainmonitors

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
import dev.fitiavana.accounting.MainActivity
import dev.fitiavana.accounting.R

/**
 * Posts one notification per fired monitor. Tapping it opens the app. Posted from
 * [ChainMonitorCheckManager], which may run with no screen open.
 */
class AndroidMonitorNotifier(context: Context) : MonitorNotifier {

    private val appContext = context.applicationContext

    override fun notifyFired(fired: FiredMonitor) {
        createChannel()
        val manager = NotificationManagerCompat.from(appContext)
        // Without the permission (Android 13+) or with notifications turned off, Home still shows the balance.
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val monitor = fired.monitor
        val open = Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(appContext, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(
                appContext.getString(
                    R.string.chain_monitor_notification_title,
                    monitor.label, MonitorAmount.format(fired.balance), monitor.tokenSymbol
                )
            )
            .setContentText(
                appContext.getString(
                    R.string.chain_monitor_notification_text,
                    MonitorAmount.format(monitor.thresholdValue), monitor.tokenSymbol
                )
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(monitor.id.hashCode(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID, appContext.getString(R.string.chain_monitor_notification_channel), NotificationManager.IMPORTANCE_DEFAULT
        )
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "chain_monitors"
    }
}
