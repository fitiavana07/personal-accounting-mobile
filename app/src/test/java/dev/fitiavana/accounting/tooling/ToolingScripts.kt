package dev.fitiavana.accounting.tooling

import java.io.File

/** Runs the repo's hook scripts (git hooks and Claude Code hooks) the way git / Claude Code does. */
object ToolingScripts {
    /** Unit tests run from the `app` module directory. */
    val repoRoot: File = File("..").canonicalFile

    class Result(val exitCode: Int, val output: String)

    /** [script] is relative to the repo root; [workingDir] is where it runs (a temp repo for the scripts that act on git). */
    fun run(
        script: String,
        args: List<String> = emptyList(),
        stdin: String = "",
        workingDir: File = repoRoot,
    ): Result {
        val process = ProcessBuilder(listOf("bash", File(repoRoot, script).path) + args)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()
        process.outputStream.use { it.write(stdin.toByteArray()) }
        val output = process.inputStream.bufferedReader().readText()
        return Result(process.waitFor(), output)
    }

    /** The JSON Claude Code sends on stdin of a PreToolUse hook. */
    fun toolInput(tool: String, vararg fields: Pair<String, String>): String =
        fields.joinToString(",", prefix = """{"tool_name":"$tool","tool_input":{""", postfix = "}}") { (key, value) ->
            "\"$key\":\"${value.escapeJson()}\""
        }

    private fun String.escapeJson() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
