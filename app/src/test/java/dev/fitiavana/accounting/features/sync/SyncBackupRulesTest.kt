package dev.fitiavana.accounting.features.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * The sync preferences hold the device id and the passphrase. A restored id would be shared by two
 * phones and the passphrase would leave the device, so they must stay out of cloud backup and device transfer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class SyncBackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** "section/domain:path" of every exclude rule in [resource], e.g. "cloud-backup/sharedpref:sync.xml". */
    private fun excludes(resource: Int): Set<String> {
        val parser = context.resources.getXml(resource)
        val found = mutableSetOf<String>()
        val path = ArrayDeque<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "exclude") {
                        val section = path.lastOrNull().orEmpty()
                        found += "$section/${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}"
                    }
                    path.addLast(parser.name)
                }
                XmlPullParser.END_TAG -> path.removeLast()
            }
        }
        return found
    }

    private val file = "sharedpref:${SyncSettingsStore.PREFS_NAME}.xml"

    @Test
    fun `auto backup before Android 12 excludes the sync preferences`() {
        assertTrue(excludes(R.xml.backup_rules).contains("full-backup-content/$file"))
    }

    @Test
    fun `cloud backup and device transfer exclude the sync preferences`() {
        val rules = excludes(R.xml.data_extraction_rules)

        assertTrue(rules.contains("cloud-backup/$file"))
        assertTrue(rules.contains("device-transfer/$file"))
        assertEquals(2, rules.size)
    }
}
