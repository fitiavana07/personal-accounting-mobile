package dev.fitiavana.accounting.ui.reports

import android.os.Looper
import android.view.View
import androidx.fragment.app.FragmentActivity
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.transactions.AddTransactionActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ReportsFragmentTest {

    @Test
    fun `tapping the FAB opens AddTransactionActivity`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java)
            .setup()
            .get()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, ReportsFragment())
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()

        activity.findViewById<View>(R.id.fab_add_transaction).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(
            AddTransactionActivity::class.java.name,
            started.component?.className
        )
    }
}
