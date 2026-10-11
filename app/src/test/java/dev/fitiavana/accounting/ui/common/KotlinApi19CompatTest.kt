package dev.fitiavana.accounting.ui.common

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Framework tint-list setters (`ImageView.imageTintList`, `View.backgroundTintList`, ...) were
 * added in API 21 and throw NoSuchMethodError on API 19. Use `ImageViewCompat` / `ViewCompat`.
 */
class KotlinApi19CompatTest {

    @Test
    fun sources_doNotCallFrameworkTintListApis() {
        val api21Tint = Regex("""(\.imageTintList|\.backgroundTintList|\.setImageTintList\(|\.setBackgroundTintList\(|\.compoundDrawableTintList)""")
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .filter { f -> f.readLines().any { api21Tint.containsMatchIn(it) && !it.contains("Compat.") } }
            .map { it.name }.toList()
        assertTrue("Use ImageViewCompat/ViewCompat tint APIs in: $offenders", offenders.isEmpty())
    }
}
