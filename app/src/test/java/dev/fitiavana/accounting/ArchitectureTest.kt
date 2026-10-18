package dev.fitiavana.accounting

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layering rules from CLAUDE.md, checked on the imports of the source files:
 * Activity/Fragment → ViewModel → Repository → DAO, and `features/` knows nothing about the UI.
 */
class ArchitectureTest {
    private val root = "dev/fitiavana/accounting"
    private val base = "dev.fitiavana.accounting"

    private val uiOnlyImports = listOf(
        "$base.ui",
        "androidx.lifecycle.ViewModel",
        "androidx.lifecycle.viewModelScope",
        "androidx.lifecycle.viewmodel",
        "androidx.fragment",
        "androidx.navigation",
        "android.view",
        "android.widget",
        "android.app.Activity",
    )

    /**
     * Not UI code: it implements the Application-level lifecycle callbacks and only counts started
     * activities, so it has to name the `Activity` type.
     */
    private val activityImportExemptions = setOf("$root/features/sync/ForegroundTracker.kt")

    /** Source file path (relative to `src/main/java`) to its content. */
    private fun sources(): Map<String, String> {
        val dir = File("src/main/java")
        return dir.walkTopDown().filter { it.extension == "kt" }
            .associate { it.relativeTo(dir).path to it.readText() }
    }

    private fun imports(content: String) =
        content.lines().map { it.trim() }.filter { it.startsWith("import ") }.map { it.removePrefix("import ") }

    private fun className(import: String) = import.substringAfterLast('.')

    private fun violations(sources: Map<String, String>): List<String> = sources.flatMap { (path, content) ->
        val fileName = path.substringAfterLast('/')
        imports(content).mapNotNull { import ->
            when {
                path.startsWith("$root/features/") && path !in activityImportExemptions &&
                    uiOnlyImports.any { import.startsWith(it) } ->
                    "$path: features/ must not depend on the UI ($import)"
                path.startsWith("$root/features/") && fileName.endsWith("Dao.kt") && className(import).endsWith("Repository") ->
                    "$path: a DAO must not depend on a repository ($import)"
                path.startsWith("$root/ui/") && className(import).endsWith("Dao") && import.startsWith("$base.features") ->
                    "$path: the UI must not use a DAO ($import)"
                path.startsWith("$root/ui/") && className(import).endsWith("Repository") &&
                    import.startsWith("$base.features") &&
                    !fileName.endsWith("ViewModel.kt") && !fileName.endsWith("ViewModelFactory.kt") ->
                    "$path: only a ViewModel (or its factory) may use a repository ($import)"
                else -> null
            }
        }
    }

    @Test
    fun theSourceTreeRespectsTheLayering() {
        val sources = sources()
        assertTrue("source files not found", sources.keys.any { it.startsWith("$root/features/") })

        assertEquals(emptyList<String>(), violations(sources))
    }

    @Test
    fun featuresCannotDependOnTheUi() {
        val bad = mapOf(
            "$root/features/x/X.kt" to "import android.widget.TextView\nimport $base.ui.home.HomeFragment",
            "$root/features/x/Y.kt" to "import androidx.lifecycle.ViewModel",
            "$root/features/x/Z.kt" to "import android.app.Activity",
        )

        assertEquals(4, violations(bad).size)
    }

    @Test
    fun aDaoCannotDependOnARepository() {
        val bad = mapOf("$root/features/x/XDao.kt" to "import $base.features.x.XRepository")

        assertEquals(1, violations(bad).size)
    }

    @Test
    fun onlyViewModelsAndTheirFactoriesMayUseRepositoriesAndNobodyInTheUiMayUseDaos() {
        val repository = "import $base.features.x.XRepository"
        val dao = "import $base.features.x.XDao"

        assertEquals(1, violations(mapOf("$root/ui/x/XFragment.kt" to repository)).size)
        assertEquals(1, violations(mapOf("$root/ui/x/XActivity.kt" to repository)).size)
        assertEquals(0, violations(mapOf("$root/ui/x/XViewModel.kt" to repository)).size)
        assertEquals(0, violations(mapOf("$root/ui/x/XViewModelFactory.kt" to repository)).size)
        assertEquals(1, violations(mapOf("$root/ui/x/XViewModel.kt" to dao)).size)
        assertEquals(0, violations(mapOf("$root/AccountingApplication.kt" to "$repository\n$dao")).size)
    }
}
