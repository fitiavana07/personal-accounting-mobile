package dev.fitiavana.accounting.features.cloudbackup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Arms the cloud backup alarm with [AlarmManager], apart from the local auto-backup's own alarm (another
 * action and request code). One fixed [PendingIntent] means each [scheduleAt] replaces the last alarm.
 * Cloud Backup needs API 23, where the alarm still fires in Doze (deferred to a maintenance window); the
 * branch below only keeps the class loadable on older versions, where nothing ever schedules it.
 */
class AlarmManagerCloudBackupScheduler(context: Context) : CloudBackupScheduler {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleAt(triggerAtMillis: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent())
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent())
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(appContext, CloudBackupReceiver::class.java).setAction(CloudBackupReceiver.ACTION_CLOUD_BACKUP)
        return PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        const val REQUEST_CODE = 1
    }
}
