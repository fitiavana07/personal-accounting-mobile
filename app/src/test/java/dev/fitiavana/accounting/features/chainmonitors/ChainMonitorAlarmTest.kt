package dev.fitiavana.accounting.features.chainmonitors

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.MainActivity
import dev.fitiavana.accounting.features.alerts.AlarmManagerAlertScheduler
import dev.fitiavana.accounting.features.backup.AlarmManagerBackupScheduler
import dev.fitiavana.accounting.features.backup.AutoBackupManager
import dev.fitiavana.accounting.features.backup.BootReceiver
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ChainMonitorAlarmTest {

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
    fun `scheduleAt arms a wakeup alarm to the monitor receiver`() {
        AlarmManagerMonitorScheduler(context).scheduleAt(1_700_000_000_000L)

        val alarm = shadowOf(alarmManager).peekNextScheduledAlarm()
        assertNotNull(alarm)
        assertEquals(1_700_000_000_000L, alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
        @Suppress("DEPRECATION")
        val intent = shadowOf(alarm.operation).savedIntent
        assertEquals(ChainMonitorCheckReceiver::class.java.name, intent.component?.className)
        assertEquals(ChainMonitorCheckReceiver.ACTION_MONITOR_CHECK, intent.action)
    }

    @Test
    fun `scheduling again replaces the previous alarm and cancel removes it`() {
        val scheduler = AlarmManagerMonitorScheduler(context)
        scheduler.scheduleAt(1_700_000_000_000L)
        scheduler.scheduleAt(1_700_000_500_000L)

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)

        scheduler.cancel()

        assertNull(shadowOf(alarmManager).peekNextScheduledAlarm())
    }

    @Test
    fun `the monitor alarm does not replace the backup or alert alarms`() {
        AlarmManagerMonitorScheduler(context).scheduleAt(1_700_000_000_000L)
        AlarmManagerAlertScheduler(context).scheduleAt(1_700_000_100_000L)
        AlarmManagerBackupScheduler(context).scheduleAt(1_700_000_200_000L)

        assertEquals(3, shadowOf(alarmManager).scheduledAlarms.size)
    }

    // --- receiver ---

    @Test
    fun `the alarm broadcast runs the check`() {
        val manager = mock<ChainMonitorCheckManager>()
        val receiver = ChainMonitorCheckReceiver().apply {
            managerProvider = { manager }
            runInBackground = { it() }
        }

        receiver.onReceive(context, Intent(ChainMonitorCheckReceiver.ACTION_MONITOR_CHECK))

        verify(manager).onAlarm()
    }

    @Test
    fun `an unrelated broadcast to the monitor receiver is ignored`() {
        val manager = mock<ChainMonitorCheckManager>()
        val receiver = ChainMonitorCheckReceiver().apply {
            managerProvider = { manager }
            runInBackground = { it() }
        }

        receiver.onReceive(context, Intent("some.other.ACTION"))

        verifyNoInteractions(manager)
    }

    @Test
    fun `boot completed re-arms the monitor alarm`() {
        val manager = mock<ChainMonitorCheckManager>()
        val receiver = BootReceiver().apply {
            managerProvider = { mock<AutoBackupManager>() }
            cloudManagerProvider = { mock() }
            alertManagerProvider = { mock() }
            chainMonitorManagerProvider = { manager }
        }

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        verify(manager).ensureScheduled()
    }

    // --- notifier ---

    private fun fired(balance: String, threshold: String = "0", label: String = "Acres 40 available") = FiredMonitor(
        ChainMonitor(
            "m1", label, "0x" + "a".repeat(40), "0x" + "b".repeat(40), "USDC", 6, threshold, 15, 1L
        ),
        BigDecimal(balance)
    )

    @Test
    fun `a fired monitor notifies with its label, balance and token, opening the app when tapped`() {
        AndroidMonitorNotifier(context).notifyFired(fired("1234.5"))

        val notification = shadowOf(notificationManager).allNotifications.single()
        val title = shadowOf(notification).contentTitle.toString()
        val text = shadowOf(notification).contentText.toString()
        assertTrue(title, title.contains("Acres 40 available"))
        assertTrue(title, title.contains("1,234.5"))
        assertTrue(title, title.contains("USDC"))
        assertTrue(text, text.contains("0"))
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun `each monitor has its own notification on its own channel, and a repeat replaces it`() {
        val notifier = AndroidMonitorNotifier(context)
        notifier.notifyFired(fired("1"))
        notifier.notifyFired(fired("2"))
        notifier.notifyFired(
            FiredMonitor(fired("1").monitor.copy(id = "m2", label = "Other"), BigDecimal("3"))
        )

        assertEquals(2, shadowOf(notificationManager).allNotifications.size)
        assertNotNull(notificationManager.getNotificationChannel(AndroidMonitorNotifier.CHANNEL_ID))
    }

    // --- amount formatting ---

    @Test
    fun `amounts are shown with grouping and no trailing zeros`() {
        assertEquals("1,234.5", MonitorAmount.format(BigDecimal("1234.500000")))
        assertEquals("0", MonitorAmount.format(BigDecimal("0.000000")))
        assertEquals("0.000001", MonitorAmount.format(BigDecimal("0.000001")))
        assertEquals("1,000,000", MonitorAmount.format(BigDecimal("1000000")))
    }
}
