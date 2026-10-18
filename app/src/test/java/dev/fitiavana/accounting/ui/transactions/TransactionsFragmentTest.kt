package dev.fitiavana.accounting.ui.transactions

import android.os.Looper
import android.view.View
import androidx.fragment.app.FragmentActivity
import dev.fitiavana.accounting.R
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class TransactionsFragmentTest {

    @Test
    fun `the screen has no FAB of its own`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, TransactionsFragment())
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(activity.findViewById<View>(R.id.fab_add_transaction))
    }
}
