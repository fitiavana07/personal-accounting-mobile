package dev.fitiavana.accounting.features.cloudbackup

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AndroidCloudBackupNotifierTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun `tells the user that backups are failing, opening the Cloud Backup screen when tapped`() {
        AndroidCloudBackupNotifier(context).notifyFailing()

        val notifications = shadowOf(manager).allNotifications
        assertEquals(1, notifications.size)
        val notification = notifications.single()
        assertEquals(context.getString(R.string.cloud_backup_notification_title), shadowOf(notification).contentTitle.toString())
        assertEquals(context.getString(R.string.cloud_backup_notification_text), shadowOf(notification).contentText.toString())
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(dev.fitiavana.accounting.ui.cloudbackup.CloudBackupActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun `the notification is on its own channel`() {
        AndroidCloudBackupNotifier(context).notifyFailing()

        assertNotNull(manager.getNotificationChannel(AndroidCloudBackupNotifier.CHANNEL_ID))
    }

    @Test
    fun `notifying again replaces the notification instead of stacking`() {
        val notifier = AndroidCloudBackupNotifier(context)
        notifier.notifyFailing()
        notifier.notifyFailing()

        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `clearing removes it`() {
        val notifier = AndroidCloudBackupNotifier(context)
        notifier.notifyFailing()

        notifier.clear()

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
