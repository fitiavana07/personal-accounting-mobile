package dev.fitiavana.accounting.features.chainmonitors

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Arms the chain monitor alarm with [AlarmManager], like the alert and backup alarms. One fixed
 * [PendingIntent] means each [scheduleAt] replaces the last alarm. Alarms are inexact (and deferred in
 * Doze on API 23+), so "every 15 minutes" is approximate; API 19 has no better scheduler.
 */
class AlarmManagerMonitorScheduler(context: Context) : MonitorScheduler {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleAt(triggerAtMillis: Long) {
        val operation = pendingIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(appContext, ChainMonitorCheckReceiver::class.java)
            .setAction(ChainMonitorCheckReceiver.ACTION_MONITOR_CHECK)
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or immutable)
    }

    private companion object {
        // 0 and 1 are the auto-backup and cloud backup alarms, 2 the alert check.
        const val REQUEST_CODE = 3
    }
}
