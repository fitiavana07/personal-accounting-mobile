package dev.fitiavana.accounting.features.changelog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The repo's own CHANGELOG.md is what ships in the APK (copied into the assets by Gradle). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class BundledChangelogTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the app bundles the current CHANGELOG md`() {
        val bundled = AssetChangelogSource(context.assets).read()

        assertEquals(File("../CHANGELOG.md").readText(), bundled)
    }

    @Test
    fun `the bundled changelog has releases with a version and entries`() {
        val releases = ChangelogRepository(AssetChangelogSource(context.assets)).getReleases()

        assertTrue(releases.isNotEmpty())
        assertTrue(releases.none { it.version.isBlank() || it.version.equals("Unreleased", ignoreCase = true) })
        assertTrue(releases.all { release -> release.groups.all { it.entries.isNotEmpty() } })
    }
}
