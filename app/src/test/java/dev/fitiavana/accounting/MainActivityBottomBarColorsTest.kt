package dev.fitiavana.accounting

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class MainActivityBottomBarColorsTest {

    private val checked = intArrayOf(android.R.attr.state_checked, android.R.attr.state_enabled)
    private val unchecked = intArrayOf(android.R.attr.state_enabled)

    private fun launch(): MainActivity =
        Robolectric.buildActivity(MainActivity::class.java).setup().get().also {
            shadowOf(Looper.getMainLooper()).idle()
        }

    private fun nav(activity: MainActivity) = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)

    private fun ColorStateList?.forState(state: IntArray) = this!!.getColorForState(state, Color.MAGENTA)

    private fun MainActivity.color(id: Int) = ContextCompat.getColor(this, id)

    @Test
    fun `the selected tab is gold and the others gray in light mode`() {
        val activity = launch()

        val tint = nav(activity).itemIconTintList
        assertEquals(activity.color(R.color.gold_500), tint.forState(checked))
        assertNotEquals(tint.forState(checked), tint.forState(unchecked))
        assertEquals(tint.forState(checked), nav(activity).itemTextColor.forState(checked))
    }

    @Test
    @Config(qualifiers = "night")
    fun `the selected tab is the lighter gold in dark mode`() {
        val activity = launch()

        val tint = nav(activity).itemIconTintList
        assertEquals(activity.color(R.color.gold_300), tint.forState(checked))
        assertEquals(tint.forState(checked), nav(activity).itemTextColor.forState(checked))
    }

    @Test
    fun `the ring around the button is white in light mode`() {
        assertEquals(Color.WHITE, launch().color(R.color.bottom_bar_surface))
    }

    @Test
    @Config(qualifiers = "night")
    fun `the ring around the button matches the raised dark bar`() {
        assertEquals(Color.parseColor("#2E2E2E"), launch().color(R.color.bottom_bar_surface))
    }
}
