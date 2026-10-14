package dev.fitiavana.accounting.ui.common

import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.fitiavana.accounting.ui.sync.SyncActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Screens draw edge-to-edge, so content (e.g. the Save button) must be padded above the nav bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class UiUtilsInsetsTest {

    @Test
    fun setupActionBar_padsContentAboveNavigationBar() {
        val activity = Robolectric.buildActivity(SyncActivity::class.java).create().get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 96))
            .build()
        ViewCompat.dispatchApplyWindowInsets(content, insets)

        assertEquals(96, content.paddingBottom)
    }
}
