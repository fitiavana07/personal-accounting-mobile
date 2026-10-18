package dev.fitiavana.accounting.tooling

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `.githooks/commit-msg`: Conventional Commits, and no Co-Authored-By trailer. */
class CommitMsgHookTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun check(message: String): ToolingScripts.Result {
        val file = folder.newFile().also { it.writeText(message) }
        return ToolingScripts.run(".githooks/commit-msg", listOf(file.path))
    }

    private fun assertAccepted(message: String) {
        val result = check(message)
        assertEquals(result.output, 0, result.exitCode)
    }

    private fun assertRejected(message: String): String {
        val result = check(message)
        assertEquals("expected rejection of: $message", 1, result.exitCode)
        return result.output
    }

    @Test
    fun acceptsConventionalCommits() {
        assertAccepted("feat(ui): add the earn screen\n")
        assertAccepted("fix: balance ignored the last transaction\n")
        assertAccepted("feat!: drop the old backup format\n")
        assertAccepted("refactor(features/transactions): extract the total\n\nLonger body.\n")
        assertAccepted("chore: bump version to 1.46 (46)\n")
    }

    @Test
    fun acceptsGitGeneratedMessages() {
        assertAccepted("Merge branch 'feature' into main\n")
        assertAccepted("Revert \"feat: add the screen\"\n")
        assertAccepted("fixup! feat: add the screen\n")
    }

    @Test
    fun ignoresCommentLinesOfTheGitTemplate() {
        assertAccepted("# Please enter the commit message\nfeat: add the screen\n")
    }

    @Test
    fun rejectsMessagesThatAreNotConventional() {
        assertRejected("Add the screen\n")
        assertRejected("feat add the screen\n")
        assertRejected("Feat: add the screen\n")
        assertRejected("feat(ui):\n")
        assertRejected("wip: stuff\n")
        assertRejected("\n")
    }

    @Test
    fun rejectsACoAuthoredByTrailerAndSaysWhy() {
        val output = assertRejected("feat: add the screen\n\nCo-Authored-By: Claude <noreply@anthropic.com>\n")
        assertTrue(output, output.contains("Co-Authored-By"))
        assertRejected("feat: add the screen\n\nco-authored-by: Someone <a@b.c>\n")
    }

    @Test
    fun theErrorShowsTheExpectedFormat() {
        assertTrue(assertRejected("Add the screen\n").contains("<type>(<scope>): <description>"))
    }

    @Test
    fun theHookFileIsCommittedAndExecutable() {
        val hook = File(ToolingScripts.repoRoot, ".githooks/commit-msg")
        assertTrue(hook.canExecute())
    }
}
