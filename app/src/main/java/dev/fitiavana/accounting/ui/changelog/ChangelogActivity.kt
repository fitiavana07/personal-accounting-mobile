package dev.fitiavana.accounting.ui.changelog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

/** The release notes: every release of CHANGELOG.md, newest first. */
class ChangelogActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, ChangelogActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_release_notes)

        val viewModel = ViewModelProvider(
            this,
            ChangelogViewModelFactory(AppContainer.getInstance(this).changelogRepository)
        ).get(ChangelogViewModel::class.java)

        val adapter = ChangelogAdapter()
        val recycler = findViewById<RecyclerView>(R.id.recycler_changelog).apply {
            layoutManager = LinearLayoutManager(this@ChangelogActivity)
            this.adapter = adapter
        }
        val emptyView = findViewById<View>(R.id.text_changelog_empty)

        viewModel.releases.observe(this) { releases ->
            adapter.submit(releases)
            val hasReleases = releases.isNotEmpty()
            recycler.visibility = if (hasReleases) View.VISIBLE else View.GONE
            emptyView.visibility = if (hasReleases) View.GONE else View.VISIBLE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
