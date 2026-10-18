package dev.fitiavana.accounting.ui.changelog

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ChangelogActivityTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun launch() =
        Robolectric.buildActivity(ChangelogActivity::class.java, ChangelogActivity.intent(context)).setup().get()
            .also { shadowOf(Looper.getMainLooper()).idle() }

    @Test
    fun `the title is Release notes`() {
        assertEquals(context.getString(R.string.title_release_notes), launch().title.toString())
    }

    @Test
    fun `lists one row per release of the bundled changelog`() {
        val releases = AppContainer.getInstance(context).changelogRepository.getReleases()
        assertTrue(releases.isNotEmpty())

        val activity = launch()
        val recycler = activity.findViewById<RecyclerView>(R.id.recycler_changelog)

        assertEquals(releases.size, recycler.adapter!!.itemCount)
        assertEquals(View.VISIBLE, recycler.visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.text_changelog_empty).visibility)
    }
}
