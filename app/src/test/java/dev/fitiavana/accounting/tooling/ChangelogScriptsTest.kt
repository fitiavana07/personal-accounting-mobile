package dev.fitiavana.accounting.tooling

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `scripts/changelog.sh` (adds one Keep a Changelog section from the Conventional Commits of a range)
 * and `scripts/backfill-changelog.sh` (one-off: one section per existing tag). Both run in a temp git repo.
 */
class ChangelogScriptsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var repo: File
    private val changelog get() = File(repo, "CHANGELOG.md")
    private var commits = 0

    @Before
    fun initRepo() {
        repo = folder.newFolder("repo")
        git("init", "-q")
    }

    private fun git(vararg args: String, date: String = "2026-01-01T12:00:00") {
        val process = ProcessBuilder(listOf("git", "-c", "user.name=t", "-c", "user.email=t@t.t") + args)
            .directory(repo)
            .redirectErrorStream(true)
            .also { it.environment()["GIT_COMMITTER_DATE"] = date; it.environment()["GIT_AUTHOR_DATE"] = date }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
    }

    private fun commit(subject: String, date: String = "2026-01-01T12:00:00") {
        File(repo, "file.txt").writeText("${commits++}")
        git("add", "file.txt")
        git("commit", "-q", "--no-verify", "-m", subject, date = date)
    }

    private fun tag(name: String, date: String, annotated: Boolean = false) =
        if (annotated) git("tag", "-a", name, "-m", name, date = date) else git("tag", name, date = date)

    private fun addSection(prev: String, ref: String, version: String, date: String) =
        ToolingScripts.run("scripts/changelog.sh", listOf("add", prev, ref, version, date), workingDir = repo)

    private fun assertAdded(prev: String, ref: String, version: String, date: String) {
        val result = addSection(prev, ref, version, date)
        assertEquals(result.output, 0, result.exitCode)
    }

    private fun backfill() = ToolingScripts.run("scripts/backfill-changelog.sh", workingDir = repo)

    private fun lines() = changelog.readLines()

    @Test
    fun createsTheFileWithTheKeepAChangelogHeader() {
        commit("feat(ui): add the earn screen")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-03")

        val text = changelog.readText()
        assertTrue(text, text.startsWith("# Changelog\n"))
        assertTrue(text, text.contains("https://keepachangelog.com/en/1.1.0/"))
        assertTrue(text, text.contains("\n## [Unreleased]\n\n## [1.0.0] - 2026-02-03\n"))
    }

    @Test
    fun mapsConventionalCommitTypesToCategories() {
        commit("feat(accounts): add search")
        commit("fix(ui): crash on rotate")
        commit("refactor: split the controller")
        commit("perf(db): index entries")
        commit("revert: drop the old export")
        commit("feat(sync)!: new protocol")
        commit("feat: add a thing without scope")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-03")

        val expected = """
            ## [1.0.0] - 2026-02-03

            ### Added

            - accounts: add search
            - add a thing without scope

            ### Changed

            - split the controller
            - db: index entries
            - sync: new protocol

            ### Removed

            - drop the old export

            ### Fixed

            - ui: crash on rotate
        """.trimIndent()
        assertTrue(changelog.readText(), changelog.readText().contains(expected))
    }

    @Test
    fun skipsMaintenanceAndNonConventionalCommits() {
        commit("feat: add a thing")
        commit("chore: bump version to 1.0 (1)")
        commit("docs: explain it")
        commit("test: cover it")
        commit("style: format")
        commit("update TASKS.md")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-03")

        assertEquals(listOf("- add a thing"), lines().filter { it.startsWith("- ") })
        assertFalse(changelog.readText(), changelog.readText().contains("### Changed"))
    }

    @Test
    fun aReleaseWithOnlyMaintenanceCommitsGetsAPlaceholderEntry() {
        commit("chore: tidy")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-03")

        assertTrue(changelog.readText(), changelog.readText().contains("### Changed\n\n- Maintenance and internal changes.\n"))
    }

    @Test
    fun onlyTakesTheCommitsAfterThePreviousTag() {
        commit("feat: old feature")
        tag("v1.0.0", "2026-01-05T10:00:00")
        commit("feat: new feature")
        assertAdded("v1.0.0", "HEAD", "1.1.0", "2026-02-03")

        val text = changelog.readText()
        assertTrue(text, text.contains("- new feature"))
        assertFalse(text, text.contains("old feature"))
    }

    @Test
    fun insertsEachNewSectionBelowUnreleasedAndAboveTheOlderOnes() {
        commit("feat: first")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-01")
        tag("v1.0.0", "2026-02-01T10:00:00")
        commit("fix: second")
        assertAdded("v1.0.0", "HEAD", "1.1.0", "2026-03-01")

        val headings = lines().filter { it.startsWith("## ") }
        assertEquals(listOf("## [Unreleased]", "## [1.1.0] - 2026-03-01", "## [1.0.0] - 2026-02-01"), headings)
        val text = changelog.readText()
        assertTrue(text, text.indexOf("- second") < text.indexOf("- first"))
    }

    @Test
    fun writesCompareLinksFromTheOriginRemote() {
        git("remote", "add", "origin", "git@github.com:someone/some-repo.git")
        commit("feat: first")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-01")
        tag("v1.0.0", "2026-02-01T10:00:00")
        commit("feat: second")
        assertAdded("v1.0.0", "HEAD", "1.1.0", "2026-03-01")

        val base = "https://github.com/someone/some-repo"
        val links = lines().filter { it.matches(Regex("""^\[[^\]]+\]: .*""")) }
        assertEquals(
            listOf(
                "[Unreleased]: $base/compare/v1.1.0...HEAD",
                "[1.1.0]: $base/compare/v1.0.0...v1.1.0",
                "[1.0.0]: $base/releases/tag/v1.0.0",
            ),
            links,
        )
        assertTrue("links stay at the end of the file", lines().takeLast(3) == links)
    }

    @Test
    fun acceptsAnHttpsRemoteToo() {
        git("remote", "add", "origin", "https://github.com/someone/some-repo.git")
        commit("feat: first")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-01")

        assertTrue(changelog.readText(), changelog.readText().contains("[Unreleased]: https://github.com/someone/some-repo/compare/v1.0.0...HEAD"))
    }

    @Test
    fun writesNoLinksWithoutAWebRemote() {
        commit("feat: first")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-01")

        assertTrue(changelog.readText(), lines().none { it.matches(Regex("""^\[[^\]]+\]: .*""")) })
    }

    @Test
    fun usageErrorsExitNonZero() {
        assertEquals(2, ToolingScripts.run("scripts/changelog.sh", listOf("add", "-"), workingDir = repo).exitCode)
        assertEquals(2, ToolingScripts.run("scripts/changelog.sh", listOf("nope"), workingDir = repo).exitCode)
    }

    @Test
    fun backfillWritesOneSectionPerTagNewestFirstWithTheTagDates() {
        // a lightweight tag takes the date of its commit, an annotated one its own
        commit("feat: initial thing", date = "2026-01-02T12:00:00")
        tag("v0.1.0", "2026-01-02T12:00:00")
        commit("feat: second thing")
        commit("fix: second fix")
        tag("v1.3", "2026-02-03T12:00:00", annotated = true)
        commit("feat: tenth thing")
        tag("v1.10", "2026-03-04T12:00:00", annotated = true)
        commit("feat: not released yet")

        val result = backfill()
        assertEquals(result.output, 0, result.exitCode)

        val headings = lines().filter { it.startsWith("## ") }
        assertEquals(
            listOf("## [Unreleased]", "## [1.10] - 2026-03-04", "## [1.3] - 2026-02-03", "## [0.1.0] - 2026-01-02"),
            headings,
        )
        val text = changelog.readText()
        assertTrue(text, text.indexOf("- tenth thing") < text.indexOf("- second thing"))
        assertTrue(text, text.indexOf("- second thing") < text.indexOf("- initial thing"))
        assertFalse("untagged commits stay out", text.contains("not released yet"))
    }

    @Test
    fun backfillRefusesToOverwriteAnExistingChangelog() {
        commit("feat: thing")
        tag("v1.0", "2026-01-02T12:00:00")
        changelog.writeText("mine\n")
        git("add", "CHANGELOG.md")
        git("commit", "-q", "-m", "docs: add changelog")

        val result = backfill()
        assertEquals(result.output, 1, result.exitCode)
        assertEquals("mine\n", changelog.readText())
    }

    @Test
    fun backfillRefusesADirtyWorkingTree() {
        commit("feat: thing")
        tag("v1.0", "2026-01-02T12:00:00")
        File(repo, "file.txt").writeText("dirty")

        val result = backfill()
        assertEquals(result.output, 1, result.exitCode)
        assertFalse(changelog.exists())
    }

    @Test
    fun backfillRefusesARepoWithoutTags() {
        commit("feat: thing")

        val result = backfill()
        assertEquals(result.output, 1, result.exitCode)
        assertFalse(changelog.exists())
    }

    @Test
    fun theScriptsAreCommittedAndExecutable() {
        listOf("scripts/changelog.sh", "scripts/backfill-changelog.sh").forEach {
            assertTrue(it, File(ToolingScripts.repoRoot, it).canExecute())
        }
    }
}
