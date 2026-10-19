package dev.fitiavana.accounting.features.cloudbackup

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.features.backup.AlarmManagerBackupScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class AlarmManagerCloudBackupSchedulerTest {

    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: AlarmManagerCloudBackupScheduler
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduler = AlarmManagerCloudBackupScheduler(context)
    }

    @Test
    fun `scheduleAt arms a wakeup alarm at the given time`() {
        scheduler.scheduleAt(1_700_000_000_000L)

        val alarm = shadowOf(alarmManager).peekNextScheduledAlarm()
        assertNotNull(alarm)
        assertEquals(1_700_000_000_000L, alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
    }

    @Test
    fun `the alarm broadcasts to the cloud backup receiver`() {
        scheduler.scheduleAt(1_700_000_000_000L)

        @Suppress("DEPRECATION")
        val operation = shadowOf(alarmManager).peekNextScheduledAlarm().operation
        val intent = shadowOf(operation).savedIntent
        assertEquals(CloudBackupReceiver::class.java.name, intent.component?.className)
        assertEquals(CloudBackupReceiver.ACTION_CLOUD_BACKUP, intent.action)
    }

    @Test
    fun `scheduling again replaces the previous alarm instead of adding another`() {
        scheduler.scheduleAt(1_700_000_000_000L)
        scheduler.scheduleAt(1_700_000_500_000L)

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(1_700_000_500_000L, shadowOf(alarmManager).peekNextScheduledAlarm().triggerAtMs)
    }

    @Test
    fun `cancel removes the alarm`() {
        scheduler.scheduleAt(1_700_000_000_000L)

        scheduler.cancel()

        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun `the cloud alarm and the local auto-backup alarm are independent`() {
        val local = AlarmManagerBackupScheduler(context)
        local.scheduleAt(1_700_000_000_000L)
        scheduler.scheduleAt(1_700_000_900_000L)

        assertEquals(2, shadowOf(alarmManager).scheduledAlarms.size)

        scheduler.cancel()

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(1_700_000_000_000L, shadowOf(alarmManager).peekNextScheduledAlarm().triggerAtMs)
    }
}
