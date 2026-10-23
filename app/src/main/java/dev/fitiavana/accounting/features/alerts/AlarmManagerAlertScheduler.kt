package dev.fitiavana.accounting.features.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Arms the hourly alert-check alarm with [AlarmManager], like the backup alarms. One fixed
 * [PendingIntent] means each [scheduleAt] replaces the last alarm. API 19 alarms are inexact, which
 * is fine for an hourly check.
 */
class AlarmManagerAlertScheduler(context: Context) : AlertScheduler {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleAt(triggerAtMillis: Long) {
        val operation = pendingIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Still fires in Doze (deferred to a maintenance window) instead of waiting for the screen to wake.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(appContext, AlertCheckReceiver::class.java)
            .setAction(AlertCheckReceiver.ACTION_ALERT_CHECK)
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or immutable)
    }

    private companion object {
        // 0 and 1 are the auto-backup and cloud backup alarms.
        const val REQUEST_CODE = 2
    }
}
