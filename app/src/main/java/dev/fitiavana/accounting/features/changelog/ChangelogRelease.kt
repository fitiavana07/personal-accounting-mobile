package dev.fitiavana.accounting.features.changelog

/** One `## [version] - date` section of CHANGELOG.md; [date] is null when the heading has none. */
data class ChangelogRelease(val version: String, val date: String?, val groups: List<ChangelogGroup>)

/** A `### Added` / `### Fixed` / ... category of a release, with its bullet entries. */
data class ChangelogGroup(val category: String, val entries: List<String>)
