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
        git("config", "user.name", "t")
        git("config", "user.email", "t@t.t")
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

    private fun sync(): ToolingScripts.Result =
        ToolingScripts.run("scripts/changelog.sh", listOf("sync"), workingDir = repo).also {
            assertEquals(it.output, 0, it.exitCode)
        }

    private fun show(version: String) =
        ToolingScripts.run("scripts/changelog.sh", listOf("show", version), workingDir = repo)

    /** The text of the bullets under Unreleased, without the hidden commit markers. */
    private fun unreleasedBullets(): List<String> =
        lines().dropWhile { it != "## [Unreleased]" }.drop(1).takeWhile { !it.startsWith("## [") }
            .filter { it.startsWith("- ") }.map { it.replace(Regex(" <!-- c:[0-9a-f]+ -->$"), "") }

    @Test
    fun syncWritesTheCommitsSinceTheLastTagUnderUnreleased() {
        commit("feat: before the tag")
        tag("v1.0.0", "2026-01-05T10:00:00")
        commit("fix(ui): crash on rotate")
        commit("chore: tidy")
        commit("feat(accounts): add search")

        val result = sync()

        assertEquals(listOf("- accounts: add search", "- ui: crash on rotate"), unreleasedBullets())
        assertTrue(result.output, result.output.contains("accounts: add search"))
        assertTrue(changelog.readText(), changelog.readText().contains("\n### Added\n\n- accounts: add search"))
    }

    @Test
    fun syncCreatesTheFileWithTheKeepAChangelogHeader() {
        commit("feat: first")
        sync()

        assertTrue(changelog.readText(), changelog.readText().startsWith("# Changelog\n"))
        assertEquals(listOf("- first"), unreleasedBullets())
    }

    @Test
    fun syncDoesNotRepeatWhatIsAlreadyThere() {
        commit("feat: first")
        sync()
        commit("fix: second")

        val result = sync()

        assertEquals(listOf("- first", "- second"), unreleasedBullets())
        assertFalse(result.output, result.output.contains("first"))
        val settled = changelog.readText()
        assertTrue(sync().output.contains("up to date"))
        assertEquals(settled, changelog.readText())
    }

    @Test
    fun syncKeepsAHandEditedBulletAndDoesNotAddItsCommitAgain() {
        commit("feat: first")
        sync()
        changelog.writeText(changelog.readText().replace("- first <!--", "- a much nicer wording <!--"))
        commit("fix: second")

        sync()

        assertEquals(listOf("- a much nicer wording", "- second"), unreleasedBullets())
    }

    @Test
    fun syncKeepsHandWrittenBulletsWithoutAMarker() {
        commit("feat: first")
        changelog.writeText("# Changelog\n\n## [Unreleased]\n\n### Added\n\n- written by hand\n  on two lines\n")

        sync()

        val text = changelog.readText()
        assertTrue(text, text.contains("- written by hand\n  on two lines\n- first <!-- c:"))
    }

    @Test
    fun syncLeavesReleasedSectionsAndLinksUntouched() {
        git("remote", "add", "origin", "git@github.com:someone/some-repo.git")
        commit("feat: released")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-01")
        tag("v1.0.0", "2026-02-01T10:00:00")
        val before = changelog.readText()
        commit("fix: later")

        sync()

        val unmarked = changelog.readText().replace(Regex(" <!-- c:[0-9a-f]+ -->"), "")
        assertEquals(before.replace("## [Unreleased]\n", "## [Unreleased]\n\n### Fixed\n\n- later\n"), unmarked)
    }

    @Test
    fun addPromotesTheUnreleasedBulletsAndAddsTheCommitsMissingFromThem() {
        commit("feat(ui): recorded")
        sync()
        changelog.writeText(changelog.readText().replace("- ui: recorded <!--", "- ui: reworded <!--"))
        commit("fix: not recorded yet")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-01")

        val expected = """
            ## [Unreleased]

            ## [1.0.0] - 2026-02-01

            ### Added

            - ui: reworded

            ### Fixed

            - not recorded yet
        """.trimIndent() + "\n"
        assertTrue(changelog.readText(), changelog.readText().endsWith(expected))
        assertFalse(changelog.readText(), changelog.readText().contains("<!--"))
    }

    @Test
    fun addKeepsHandEditedWordingAndContinuationLinesOfUnreleased() {
        commit("feat: a")
        changelog.writeText(
            "# Changelog\n\n## [Unreleased]\n\n### Added\n\n- nicer wording\n  on two lines\n- another one\n",
        )
        assertAdded("-", "HEAD", "1.0.0", "2026-02-01")

        val text = changelog.readText()
        assertTrue(text, text.contains("### Added\n\n- nicer wording\n  on two lines\n- another one\n- a\n"))
    }

    @Test
    fun showPrintsTheBulletsOfAReleaseWithoutMarkdownHeadings() {
        commit("feat(ui): add the earn screen")
        commit("fix: crash on rotate")
        commit("feat: older release")
        assertAdded("-", "HEAD", "1.0.0", "2026-02-01")
        tag("v1.0.0", "2026-02-01T10:00:00")
        commit("feat(sync): newer one")
        assertAdded("v1.0.0", "HEAD", "1.1.0", "2026-03-01")

        val result = show("1.0.0")

        assertEquals(result.output, 0, result.exitCode)
        assertEquals(
            "Added\n  - ui: add the earn screen\n  - older release\n\nFixed\n  - crash on rotate\n",
            result.output,
        )
        assertEquals("Added\n  - sync: newer one\n", show("1.1.0").output)
    }

    private fun gitOutput(vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args).directory(repo).redirectErrorStream(true).start()
        return process.inputStream.bufferedReader().readText().also { process.waitFor() }
    }

    private fun syncAndCommit() =
        ToolingScripts.run("scripts/changelog.sh", listOf("commit"), workingDir = repo).also {
            assertEquals(it.output, 0, it.exitCode)
        }

    @Test
    fun commitSyncsThenCommitsOnlyTheChangelog() {
        commit("feat(ui): add the earn screen")
        File(repo, "other.txt").writeText("staged, not mine")
        git("add", "other.txt")

        val result = syncAndCommit()

        assertTrue(result.output, result.output.contains("ui: add the earn screen"))
        assertEquals("docs(changelog): update Unreleased entries", gitOutput("log", "-1", "--format=%s").trim())
        assertEquals("CHANGELOG.md", gitOutput("show", "--name-only", "--format=", "HEAD").trim())
        assertEquals("other.txt", gitOutput("diff", "--cached", "--name-only").trim())
        assertEquals(listOf("- ui: add the earn screen"), unreleasedBullets())
        assertTrue(gitOutput("log", "-1", "--format=%b").contains("- ui: add the earn screen"))
    }

    @Test
    fun commitDoesNothingWhenTheChangelogIsUpToDate() {
        commit("feat: first")
        syncAndCommit()
        val head = gitOutput("rev-parse", "HEAD")

        val result = syncAndCommit()

        assertTrue(result.output, result.output.contains("up to date"))
        assertEquals(head, gitOutput("rev-parse", "HEAD"))
    }

    @Test
    fun theCommitOfTheChangelogIsNotListedInTheChangelog() {
        commit("feat: first")
        syncAndCommit()
        syncAndCommit()

        assertEquals(listOf("- first"), unreleasedBullets())
    }

    @Test
    fun theScriptsAreCommittedAndExecutable() {
        listOf("scripts/changelog.sh", "scripts/backfill-changelog.sh").forEach {
            assertTrue(it, File(ToolingScripts.repoRoot, it).canExecute())
        }
    }
}
