package dev.fitiavana.accounting.tooling

import dev.fitiavana.accounting.tooling.ToolingScripts.toolInput
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The Claude Code PreToolUse hooks in `.claude/hooks`; exit code 2 blocks the tool call. */
class ClaudeHooksTest {
    private val gradleFile = "app/build.gradle.kts"

    @Before
    fun needsJq() {
        val hasJq = runCatching { ProcessBuilder("jq", "--version").start().waitFor() == 0 }.getOrDefault(false)
        assumeTrue("jq is required by the hooks", hasJq)
    }

    private fun run(script: String, stdin: String) = ToolingScripts.run(".claude/hooks/$script", stdin = stdin)

    private fun assertBlocked(script: String, stdin: String, reason: String? = null) {
        val result = run(script, stdin)
        assertEquals(result.output, 2, result.exitCode)
        if (reason != null) assertTrue(result.output, result.output.contains(reason))
    }

    private fun assertAllowed(script: String, stdin: String) {
        val result = run(script, stdin)
        assertEquals(result.output, 0, result.exitCode)
    }

    // block-local-properties.sh

    @Test
    fun localPropertiesCannotBeRead() {
        val script = "block-local-properties.sh"
        assertBlocked(script, toolInput("Read", "file_path" to "/x/accounting/local.properties"))
        assertBlocked(script, toolInput("Bash", "command" to "cat local.properties"))
        assertBlocked(script, toolInput("Grep", "pattern" to "key", "path" to "local.properties"))
        assertAllowed(script, toolInput("Read", "file_path" to "/x/accounting/gradle.properties"))
        assertAllowed(script, toolInput("Bash", "command" to "./gradlew assembleDebug"))
    }

    // block-no-verify.sh

    @Test
    fun gitHooksCannotBeBypassed() {
        val script = "block-no-verify.sh"
        assertBlocked(script, toolInput("Bash", "command" to "git commit --no-verify -m 'feat: x'"), "commit-msg")
        assertBlocked(script, toolInput("Bash", "command" to "git commit -n -m 'feat: x'"))
        assertBlocked(script, toolInput("Bash", "command" to "git commit -anm 'feat: x'"))
        assertBlocked(script, toolInput("Bash", "command" to "git config core.hooksPath /dev/null"))
        assertBlocked(script, toolInput("Bash", "command" to "git -c core.hooksPath=/dev/null commit -m 'feat: x'"))
        assertAllowed(script, toolInput("Bash", "command" to "git commit -m 'feat: add the screen'"))
        assertAllowed(script, toolInput("Bash", "command" to "git commit --amend -m 'fix: total'"))
        assertAllowed(script, toolInput("Bash", "command" to "git status"))
    }

    // guard-build-files.sh

    @Test
    fun versionsCannotBeBumpedByHand() {
        val script = "guard-build-files.sh"
        assertBlocked(
            script,
            toolInput("Edit", "file_path" to gradleFile, "old_string" to "versionCode = 1", "new_string" to "versionCode = 2"),
            "release.sh",
        )
        assertBlocked(
            script,
            toolInput("Edit", "file_path" to gradleFile, "old_string" to "versionName = \"1.0\"", "new_string" to "versionName = \"1.1\""),
        )
        assertAllowed(
            script,
            toolInput("Edit", "file_path" to gradleFile, "old_string" to "versionNameSuffix = \"-dev\"", "new_string" to "versionNameSuffix = \"-debug\""),
        )
    }

    @Test
    fun rewritingTheBuildFileCannotChangeTheVersion() {
        val script = "guard-build-files.sh"
        val current = File(ToolingScripts.repoRoot, gradleFile).readText()
        assertAllowed(script, toolInput("Write", "file_path" to gradleFile, "content" to current))
        assertBlocked(
            script,
            toolInput("Write", "file_path" to gradleFile, "content" to current.replace("versionCode = ", "versionCode = 9")),
        )
    }

    @Test
    fun dependenciesMustGoThroughTheVersionCatalog() {
        val script = "guard-build-files.sh"
        fun edit(newString: String) =
            toolInput("Edit", "file_path" to gradleFile, "old_string" to "", "new_string" to newString)
        assertBlocked(script, edit("implementation(\"com.foo:bar:1.0\")"), "libs.versions.toml")
        assertBlocked(script, edit("    testImplementation( \"junit:junit:4.13.2\")"))
        assertBlocked(script, edit("implementation(platform(\"androidx.foo:foo-bom:1\"))"))
        assertAllowed(script, edit("implementation(libs.androidx.room.runtime)"))
        assertAllowed(script, edit("implementation(platform(libs.androidx.foo.bom))"))
    }

    @Test
    fun otherFilesAreNotGuarded() {
        val script = "guard-build-files.sh"
        assertAllowed(
            script,
            toolInput("Edit", "file_path" to "app/src/main/Foo.kt", "old_string" to "a", "new_string" to "versionCode = 3"),
        )
    }
}
