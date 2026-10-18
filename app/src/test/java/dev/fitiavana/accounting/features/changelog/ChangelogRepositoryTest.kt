package dev.fitiavana.accounting.features.changelog

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogRepositoryTest {

    @Test
    fun `parses what the source reads`() {
        val repository = ChangelogRepository { "## [1.0] - 2026-01-01\n\n### Added\n\n- thing\n" }

        val release = repository.getReleases().single()

        assertEquals("1.0", release.version)
        assertEquals(listOf("thing"), release.groups.single().entries)
    }

    @Test
    fun `an unreadable source gives no releases instead of failing`() {
        val repository = ChangelogRepository { throw IOException("missing") }

        assertTrue(repository.getReleases().isEmpty())
    }
}
