package dev.fitiavana.accounting.features.changelog

import android.content.res.AssetManager
import java.io.IOException

/** Where the text of CHANGELOG.md comes from. */
fun interface ChangelogSource {
    @Throws(IOException::class)
    fun read(): String
}

/** The CHANGELOG.md bundled in the APK (Gradle copies the repo's file into the assets at build time). */
class AssetChangelogSource(private val assets: AssetManager) : ChangelogSource {
    override fun read(): String = assets.open(FILE_NAME).bufferedReader().use { it.readText() }

    companion object {
        const val FILE_NAME = "CHANGELOG.md"
    }
}

class ChangelogRepository(private val source: ChangelogSource) {

    /** Newest first, as in the file; empty when the changelog cannot be read. */
    fun getReleases(): List<ChangelogRelease> =
        try {
            ChangelogParser.parse(source.read())
        } catch (e: IOException) {
            emptyList()
        }
}
