package dev.fitiavana.accounting.features.alerts

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.MainActivity
import dev.fitiavana.accounting.features.backup.AutoBackupManager
import dev.fitiavana.accounting.features.backup.BootReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AlertAlarmTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // --- scheduler ---

    @Test
    fun `scheduleAt arms a wakeup alarm to the alert receiver`() {
        AlarmManagerAlertScheduler(context).scheduleAt(1_700_000_000_000L)

        val alarm = shadowOf(alarmManager).peekNextScheduledAlarm()
        assertNotNull(alarm)
        assertEquals(1_700_000_000_000L, alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
        @Suppress("DEPRECATION")
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(AlertCheckReceiver::class.java.name, intent.component?.className)
        assertEquals(AlertCheckReceiver.ACTION_ALERT_CHECK, intent.action)
    }

    @Test
    fun `scheduling again replaces the previous alarm and cancel removes it`() {
        val scheduler = AlarmManagerAlertScheduler(context)
        scheduler.scheduleAt(1_700_000_000_000L)
        scheduler.scheduleAt(1_700_000_500_000L)

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        scheduler.cancel()

        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun `the alert alarm does not replace the backup alarms`() {
        AlarmManagerAlertScheduler(context).scheduleAt(1_700_000_000_000L)
        dev.fitiavana.accounting.features.backup.AlarmManagerBackupScheduler(context).scheduleAt(1_700_000_100_000L)

        assertEquals(2, shadowOf(alarmManager).scheduledAlarms.size)
    }

    // --- receiver ---

    @Test
    fun `the alarm broadcast runs the check`() {
        val manager = mock<AlertCheckManager>()
        val receiver = AlertCheckReceiver().apply {
            managerProvider = { manager }
            runInBackground = { it() }
        }

        receiver.onReceive(context, Intent(AlertCheckReceiver.ACTION_ALERT_CHECK))

        verify(manager).onAlarm()
    }

    @Test
    fun `an unrelated broadcast to the alert receiver is ignored`() {
        val manager = mock<AlertCheckManager>()
        val receiver = AlertCheckReceiver().apply {
            managerProvider = { manager }
            runInBackground = { it() }
        }

        receiver.onReceive(context, Intent("some.other.ACTION"))

        org.mockito.kotlin.verifyNoInteractions(manager)
    }

    @Test
    fun `boot completed re-arms the alert alarm`() {
        val manager = mock<AlertCheckManager>()
        val receiver = BootReceiver().apply {
            managerProvider = { mock<AutoBackupManager>() }
            cloudManagerProvider = { mock() }
            alertManagerProvider = { manager }
        }

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        verify(manager).ensureScheduled()
    }

    // --- notifier ---

    private fun fired(percent: Double, type: String = AlertTypes.CROSSING_ABOVE, threshold: Double = 10.0) =
        FiredAlert(GainLossAlert("al1", "acc", type, threshold, 1L), percent)

    @Test
    fun `a fired gain alert notifies with the account, percent and threshold, opening the app when tapped`() {
        AndroidAlertNotifier(context).notifyFired(fired(12.34), "My BTC")

        val notifications = shadowOf(notificationManager).allNotifications
        assertEquals(1, notifications.size)
        val notification = notifications.single()
        val title = shadowOf(notification).contentTitle.toString()
        val text = shadowOf(notification).contentText.toString()
        assertTrue(title, title.contains("My BTC"))
        assertTrue(title, title.contains("+12.3%"))
        assertTrue(text, text.contains("+10"))
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun `a fired loss alert shows a negative percent`() {
        AndroidAlertNotifier(context).notifyFired(fired(-12.0, AlertTypes.CROSSING_BELOW, -10.0), "My BTC")

        val title = shadowOf(shadowOf(notificationManager).allNotifications.single()).contentTitle.toString()
        assertTrue(title, title.contains("-12.0%"))
    }

    @Test
    fun `each alert has its own notification on its own channel`() {
        val notifier = AndroidAlertNotifier(context)
        notifier.notifyFired(fired(12.0), "A")
        notifier.notifyFired(
            FiredAlert(GainLossAlert("al2", "acc", AlertTypes.CROSSING_ABOVE, 5.0, 1L), 6.0),
            "B"
        )

        assertEquals(2, shadowOf(notificationManager).allNotifications.size)
        assertNotNull(notificationManager.getNotificationChannel(AndroidAlertNotifier.CHANNEL_ID))
    }

    @Test
    fun `the same alert firing again replaces its notification`() {
        val notifier = AndroidAlertNotifier(context)
        notifier.notifyFired(fired(12.0), "A")
        notifier.notifyFired(fired(13.0), "A")

        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }
}
