package dev.fitiavana.accounting

import android.os.Looper
import android.view.Menu
import dev.fitiavana.accounting.ui.changelog.ChangelogActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenu

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class MainMenuReleaseNotesTest {

    private lateinit var activity: MainActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun inflateMenu(): Menu = RoboMenu(activity).also { activity.onCreateOptionsMenu(it) }

    @Test
    fun `the overflow menu offers the release notes`() {
        val item = inflateMenu().findItem(R.id.action_release_notes)

        assertNotNull(item)
        assertEquals(activity.getString(R.string.action_release_notes), item.title.toString())
    }

    @Test
    fun `selecting it opens the release notes screen`() {
        activity.onOptionsItemSelected(inflateMenu().findItem(R.id.action_release_notes))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ChangelogActivity::class.java.name, started.component!!.className)
    }
}
