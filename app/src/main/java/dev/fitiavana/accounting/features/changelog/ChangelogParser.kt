package dev.fitiavana.accounting.features.changelog

/**
 * Reads CHANGELOG.md in the Keep a Changelog format (what scripts/changelog.sh writes). The preamble, the
 * `[Unreleased]` section and the link references at the end are not releases and are skipped.
 */
object ChangelogParser {
    private val releaseHeading = Regex("""^##\s+\[([^\]]+)](?:\s+-\s+(\d{4}-\d{2}-\d{2}))?.*$""")
    private val categoryHeading = Regex("""^###\s+(.+?)\s*$""")
    private val bullet = Regex("""^[-*]\s+(.*)$""")
    private const val UNRELEASED = "Unreleased"

    fun parse(text: String): List<ChangelogRelease> {
        val releases = mutableListOf<ChangelogRelease>()
        var current: ReleaseBuilder? = null

        fun finishRelease() {
            current?.let { releases += it.build() }
            current = null
        }

        for (line in text.lines()) {
            val heading = releaseHeading.matchEntire(line)
            if (heading != null) {
                finishRelease()
                val version = heading.groupValues[1]
                if (!version.equals(UNRELEASED, ignoreCase = true)) {
                    current = ReleaseBuilder(version, heading.groupValues[2].ifEmpty { null })
                }
                continue
            }
            val release = current ?: continue
            val category = categoryHeading.matchEntire(line)
            val entry = bullet.matchEntire(line)
            when {
                category != null -> release.startCategory(category.groupValues[1])
                entry != null -> release.addEntry(entry.groupValues[1])
                line.isNotBlank() && line.first().isWhitespace() -> release.continueEntry(line.trim())
            }
        }
        finishRelease()
        return releases
    }

    private class ReleaseBuilder(private val version: String, private val date: String?) {
        private val groups = mutableListOf<ChangelogGroup>()
        private var category: String? = null
        private val entries = mutableListOf<String>()

        fun startCategory(name: String) {
            closeCategory()
            category = name
        }

        fun addEntry(text: String) {
            if (category != null) entries += text.trim()
        }

        fun continueEntry(text: String) {
            if (entries.isNotEmpty()) entries[entries.lastIndex] = entries.last() + " " + text
        }

        fun build(): ChangelogRelease {
            closeCategory()
            return ChangelogRelease(version, date, groups.toList())
        }

        private fun closeCategory() {
            category?.let { if (entries.isNotEmpty()) groups += ChangelogGroup(it, entries.toList()) }
            category = null
            entries.clear()
        }
    }
}
