package dev.fitiavana.accounting.ui.changelog

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.changelog.ChangelogGroup
import dev.fitiavana.accounting.features.changelog.ChangelogRelease
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class ChangelogAdapterTest {

    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Accounting)

    private val releases = listOf(
        ChangelogRelease(
            version = "1.2",
            date = "2026-02-01",
            groups = listOf(
                ChangelogGroup("Added", listOf("ui: add thing", "accounts: another")),
                ChangelogGroup("Fixed", listOf("sync: fix it"))
            )
        ),
        ChangelogRelease(version = "1.1", date = null, groups = listOf(ChangelogGroup("Changed", listOf("tidy"))))
    )

    private fun bindAt(adapter: ChangelogAdapter, position: Int): View {
        val holder = adapter.onCreateViewHolder(FrameLayout(context), adapter.getItemViewType(position))
        adapter.onBindViewHolder(holder, position)
        return holder.itemView
    }

    private fun adapter() = ChangelogAdapter().also { it.submit(releases) }

    private fun bodyTexts(view: View): List<String> {
        val body = view.findViewById<LinearLayout>(R.id.layout_release_body)
        return (0 until body.childCount).map { (body.getChildAt(it) as TextView).text.toString() }
    }

    @Test
    fun `one row per release`() {
        assertEquals(2, adapter().itemCount)
    }

    @Test
    fun `a row shows the version, the date and each category followed by its bulleted entries`() {
        val view = bindAt(adapter(), 0)

        assertEquals("1.2", view.findViewById<TextView>(R.id.text_release_version).text.toString())
        assertEquals("2026-02-01", view.findViewById<TextView>(R.id.text_release_date).text.toString())
        assertEquals(
            listOf("Added", "• ui: add thing", "• accounts: another", "Fixed", "• sync: fix it"),
            bodyTexts(view)
        )
    }

    @Test
    fun `a release without a date hides the date`() {
        val view = bindAt(adapter(), 1)

        assertEquals(View.GONE, view.findViewById<View>(R.id.text_release_date).visibility)
    }

    @Test
    fun `rebinding a row does not pile up entries`() {
        val adapter = adapter()
        val holder = adapter.onCreateViewHolder(FrameLayout(context), adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        adapter.onBindViewHolder(holder, 1)
        adapter.onBindViewHolder(holder, 0)

        assertEquals(5, (holder.itemView.findViewById<ViewGroup>(R.id.layout_release_body)).childCount)
    }
}
