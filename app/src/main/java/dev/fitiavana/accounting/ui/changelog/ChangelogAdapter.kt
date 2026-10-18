package dev.fitiavana.accounting.ui.changelog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.changelog.ChangelogRelease

/** One card per release: version and date, then each category followed by its bulleted entries. */
class ChangelogAdapter : RecyclerView.Adapter<ChangelogAdapter.ViewHolder>() {

    private var releases: List<ChangelogRelease> = emptyList()

    fun submit(releases: List<ChangelogRelease>) {
        this.releases = releases
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = releases.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_changelog_release, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(releases[position])

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateView = itemView.findViewById<TextView>(R.id.text_release_date)
        private val body = itemView.findViewById<LinearLayout>(R.id.layout_release_body)

        fun bind(release: ChangelogRelease) {
            itemView.findViewById<TextView>(R.id.text_release_version).text = release.version
            dateView.text = release.date.orEmpty()
            dateView.visibility = if (release.date == null) View.GONE else View.VISIBLE

            body.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)
            release.groups.forEach { group ->
                addRow(inflater, R.layout.item_changelog_category, group.category)
                group.entries.forEach { entry ->
                    addRow(inflater, R.layout.item_changelog_entry, itemView.context.getString(R.string.changelog_entry, entry))
                }
            }
        }

        private fun addRow(inflater: LayoutInflater, layout: Int, text: String) {
            (inflater.inflate(layout, body, false) as TextView).also {
                it.text = text
                body.addView(it)
            }
        }
    }
}
