package dev.fitiavana.accounting

import android.content.Context
import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class MainMenuCloudBackupTest {
    @Test
    fun `the overflow menu offers cloud backup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val menu = PopupMenu(context, View(context)).menu
        MenuInflater(context).inflate(R.menu.menu_main, menu)

        val item = menu.findItem(R.id.action_cloud_backup)

        assertNotNull(item)
        assertEquals(context.getString(R.string.action_cloud_backup), item.title.toString())
    }
}
