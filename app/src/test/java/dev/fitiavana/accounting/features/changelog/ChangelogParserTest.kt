package dev.fitiavana.accounting.features.changelog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogParserTest {

    private val sample = """
        # Changelog

        All notable changes to this project are documented in this file.

        ## [Unreleased]

        ### Added

        - not released yet

        ## [1.2] - 2026-02-01

        ### Added

        - ui: add thing
        * accounts: star bullet

        ### Fixed

        - sync: fix it

        ## [1.1] - 2026-01-01

        ### Changed

        - Maintenance and internal changes.

        [Unreleased]: https://example.com/compare/v1.2...HEAD
        [1.2]: https://example.com/compare/v1.1...v1.2
        [1.1]: https://example.com/releases/tag/v1.1
    """.trimIndent()

    @Test
    fun `lists the releases newest first as in the file, without the preamble, Unreleased or link references`() {
        val releases = ChangelogParser.parse(sample)

        assertEquals(listOf("1.2", "1.1"), releases.map { it.version })
        assertTrue(releases.flatMap { it.groups }.flatMap { it.entries }.none { it.contains("example.com") })
    }

    @Test
    fun `reads the date of each release`() {
        assertEquals(listOf("2026-02-01", "2026-01-01"), ChangelogParser.parse(sample).map { it.date })
    }

    @Test
    fun `a release without a date has a null date`() {
        val release = ChangelogParser.parse("## [1.0]\n\n### Added\n\n- thing\n").single()

        assertEquals("1.0", release.version)
        assertNull(release.date)
    }

    @Test
    fun `a trailing yanked marker does not end up in the version or date`() {
        val release = ChangelogParser.parse("## [1.0] - 2026-01-01 [YANKED]\n\n### Fixed\n\n- thing\n").single()

        assertEquals("1.0", release.version)
        assertEquals("2026-01-01", release.date)
    }

    @Test
    fun `keeps the categories and their entries in file order, for dash and star bullets`() {
        val release = ChangelogParser.parse(sample).first()

        assertEquals(listOf("Added", "Fixed"), release.groups.map { it.category })
        assertEquals(listOf("ui: add thing", "accounts: star bullet"), release.groups[0].entries)
        assertEquals(listOf("sync: fix it"), release.groups[1].entries)
    }

    @Test
    fun `an indented continuation line is joined to its entry`() {
        val release = ChangelogParser.parse("## [1.0] - 2026-01-01\n\n### Added\n\n- first part\n  second part\n- next\n").single()

        assertEquals(listOf("first part second part", "next"), release.groups.single().entries)
    }

    @Test
    fun `a release without categories is kept with no groups`() {
        val release = ChangelogParser.parse("## [1.0] - 2026-01-01\n\n## [0.9] - 2025-12-01\n").first()

        assertEquals("1.0", release.version)
        assertTrue(release.groups.isEmpty())
    }

    @Test
    fun `a category without entries is left out`() {
        val release = ChangelogParser.parse("## [1.0] - 2026-01-01\n\n### Added\n\n### Fixed\n\n- thing\n").single()

        assertEquals(listOf("Fixed"), release.groups.map { it.category })
    }

    @Test
    fun `blank or header-only text gives no releases`() {
        assertTrue(ChangelogParser.parse("").isEmpty())
        assertTrue(ChangelogParser.parse("# Changelog\n\n## [Unreleased]\n").isEmpty())
    }
}
