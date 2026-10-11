package dev.fitiavana.accounting.ui.common

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `android:tint="?attr/..."` on an ImageView crashes inflation on API 19 (theme attributes in
 * ColorStateList tints are only resolved from API 21). `app:tint` (AppCompat) works everywhere.
 */
class LayoutApi19CompatTest {

    @Test
    fun layouts_doNotUseThemeAttrAndroidTint() {
        val layoutDir = File("src/main/res/layout")
        val offenders = layoutDir.listFiles { f -> f.extension == "xml" }!!
            .filter { Regex("""android:tint="\?""").containsMatchIn(it.readText()) }
            .map { it.name }
        assertTrue("Use app:tint instead of android:tint with ?attr in: $offenders", offenders.isEmpty())
    }

    /** Vector drawables referenced via `android:src` fail to inflate before API 21; use `app:srcCompat`. */
    @Test
    fun layouts_doNotReferenceVectorDrawablesViaAndroidSrc() {
        val res = File("src/main/res")
        val vectors = File(res, "drawable").listFiles { f -> f.extension == "xml" }!!
            .filter { it.readText().contains("<vector") }
            .map { it.nameWithoutExtension }.toSet()
        val srcRef = Regex("""android:src="@drawable/([A-Za-z0-9_]+)"""")
        val offenders = File(res, "layout").listFiles { f -> f.extension == "xml" }!!
            .filter { f -> srcRef.findAll(f.readText()).any { it.groupValues[1] in vectors } }
            .map { it.name }
        assertTrue("Use app:srcCompat for vector drawables in: $offenders", offenders.isEmpty())
    }
}
