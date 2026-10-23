package dev.fitiavana.accounting.features.alerts

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
import dev.fitiavana.accounting.features.balances.SignedPercent

/**
 * Posts one notification per fired alert. Tapping it opens the app, on Home. Posted from
 * [AlertCheckManager], which may run with no screen open.
 */
class AndroidAlertNotifier(context: Context) : AlertNotifier {

    private val appContext = context.applicationContext

    override fun notifyFired(fired: FiredAlert, accountName: String) {
        createChannel()
        val manager = NotificationManagerCompat.from(appContext)
        // Without the permission (Android 13+) or with notifications turned off, Home still shows the %.
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(appContext, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val threshold = SignedPercent.format(fired.alert.thresholdPercent)
        val text = appContext.getString(
            if (fired.alert.type == AlertTypes.CROSSING_ABOVE) R.string.alert_notification_text_above
            else R.string.alert_notification_text_below,
            threshold
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(appContext.getString(R.string.alert_notification_title, accountName, SignedPercent.format(fired.percent)))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(fired.alert.id.hashCode(), notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID, appContext.getString(R.string.alert_notification_channel), NotificationManager.IMPORTANCE_DEFAULT
        )
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "gain_loss_alerts"
    }
}
