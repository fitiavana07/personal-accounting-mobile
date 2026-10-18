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
class MainMenuSyncTest {
    @Test
    fun `the overflow menu offers sync with other devices`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val menu = PopupMenu(context, View(context)).menu
        MenuInflater(context).inflate(R.menu.menu_main, menu)

        val item = menu.findItem(R.id.action_sync)

        assertNotNull(item)
        assertEquals(context.getString(R.string.sync), item.title.toString())
    }
}
