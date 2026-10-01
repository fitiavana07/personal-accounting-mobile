package dev.fitiavana.accounting.ui.home

import android.os.Looper
import android.view.View
import androidx.fragment.app.FragmentActivity
import dev.fitiavana.accounting.R
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.ui.cexprices.CexPricesActivity
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
class HomeFragmentTest {

    private fun launchHomeFragment(): FragmentActivity {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java)
            .setup()
            .get()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, HomeFragment())
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    @Test
    fun `tapping the FAB opens AddTransactionActivity`() {
        val activity = launchHomeFragment()

        val fab = activity.findViewById<View>(R.id.fab_add_transaction)
        fab.performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(
            AddTransactionActivity::class.java.name,
            started.component?.className
        )
    }

    private fun layOutRecycler(activity: FragmentActivity): RecyclerView {
        val recycler = activity.findViewById<RecyclerView>(R.id.recycler_home)
        recycler.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY)
        )
        recycler.layout(0, 0, 1080, 4000)
        return recycler
    }

    @Test
    fun `tapping the CEX prices shortcut opens CexPricesActivity`() {
        val activity = launchHomeFragment()
        layOutRecycler(activity)

        activity.findViewById<View>(R.id.button_cex_prices).performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(CexPricesActivity::class.java.name, started.component?.className)
    }

    @Test
    fun `shortcuts row and P2P prices sit between the metrics and emergency fund blocks`() {
        val activity = launchHomeFragment()
        val recycler = layOutRecycler(activity)

        fun indexOf(id: Int) = (0 until recycler.childCount).indexOfFirst {
            recycler.getChildAt(it).findViewById<View>(id) != null
        }
        val metrics = indexOf(R.id.container_metrics_rows)
        val shortcuts = indexOf(R.id.button_cex_prices)
        val p2p = indexOf(R.id.text_p2p_buy_title)
        val emergency = indexOf(R.id.progress_emergency_fund_6month)

        assertEquals(metrics + 1, shortcuts)
        assertEquals(shortcuts + 1, p2p)
        assertEquals(p2p + 1, emergency)
    }
}
