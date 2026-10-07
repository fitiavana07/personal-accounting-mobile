package dev.fitiavana.accounting.features.backup

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
@Config(sdk = [21])
class AlarmManagerBackupSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: AlarmManagerBackupScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduler = AlarmManagerBackupScheduler(context)
    }

    @Test
    fun `scheduleAt arms a wakeup alarm at the given time`() {
        scheduler.scheduleAt(1_700_000_000_000L)

        val alarm = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull(alarm)
        assertEquals(1_700_000_000_000L, alarm.triggerAtTime)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
    }

    @Test
    fun `the alarm broadcasts to the auto-backup receiver`() {
        scheduler.scheduleAt(1_700_000_000_000L)

        val alarm = shadowOf(alarmManager).nextScheduledAlarm
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(AutoBackupReceiver::class.java.name, intent.component?.className)
        assertEquals(AutoBackupReceiver.ACTION_AUTO_BACKUP, intent.action)
    }

    @Test
    fun `scheduling again replaces the previous alarm instead of adding another`() {
        scheduler.scheduleAt(1_700_000_000_000L)
        scheduler.scheduleAt(1_700_000_500_000L)

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        assertEquals(1_700_000_500_000L, shadowOf(alarmManager).nextScheduledAlarm.triggerAtTime)
    }

    @Test
    fun `cancel removes the alarm`() {
        scheduler.scheduleAt(1_700_000_000_000L)

        scheduler.cancel()

        assertNull(shadowOf(alarmManager).nextScheduledAlarm)
    }
}
