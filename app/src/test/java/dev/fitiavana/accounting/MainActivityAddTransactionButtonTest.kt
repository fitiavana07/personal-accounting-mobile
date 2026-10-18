package dev.fitiavana.accounting

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import dev.fitiavana.accounting.ui.transactions.AddTransactionActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class MainActivityAddTransactionButtonTest {

    private lateinit var activity: MainActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `tapping the center button opens AddTransactionActivity`() {
        val button = activity.findViewById<View>(R.id.fab_add_transaction)
        assertNotNull(button)

        button.performClick()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(AddTransactionActivity::class.java.name, started.component?.className)
    }

    @Test
    fun `the bar leaves a disabled middle slot with two tabs on each side`() {
        val menu = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).menu

        assertEquals(5, menu.size())
        assertEquals(R.id.nav_add_placeholder, menu.getItem(2).itemId)
        assertFalse(menu.getItem(2).isEnabled)
    }

    @Test
    fun `the button stays after switching tabs`() {
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)

        nav.selectedItemId = R.id.nav_accounts
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity.findViewById<View>(R.id.fab_add_transaction))
    }

    @Test
    fun `the middle slot's view ignores touches`() {
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)

        assertFalse(nav.findViewById<View>(R.id.nav_add_placeholder).isEnabled)
    }

    @Test
    fun `every tab shows its label`() {
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)

        assertEquals(NavigationBarView.LABEL_VISIBILITY_LABELED, nav.labelVisibilityMode)
    }

    @Test
    fun `the button is centered on the bar's top edge`() {
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        val container = activity.findViewById<View>(R.id.add_transaction_container)
        assertTrue(container.height > 0)

        val params = container.layoutParams as FrameLayout.LayoutParams

        assertEquals(nav.height - container.height / 2, params.bottomMargin)
    }
}
