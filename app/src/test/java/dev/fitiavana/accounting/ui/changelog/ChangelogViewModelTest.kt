package dev.fitiavana.accounting.ui.changelog

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import dev.fitiavana.accounting.features.changelog.ChangelogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChangelogViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun `exposes the releases of the repository`() {
        val viewModel = ChangelogViewModel(
            ChangelogRepository { "## [1.1] - 2026-02-01\n\n### Fixed\n\n- b\n\n## [1.0] - 2026-01-01\n\n### Added\n\n- a\n" }
        )

        assertEquals(listOf("1.1", "1.0"), viewModel.releases.value!!.map { it.version })
    }

    @Test
    fun `exposes an empty list when there is nothing to show`() {
        val viewModel = ChangelogViewModel(ChangelogRepository { "" })

        assertTrue(viewModel.releases.value!!.isEmpty())
    }
}
