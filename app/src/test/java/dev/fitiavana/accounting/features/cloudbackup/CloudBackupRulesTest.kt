package dev.fitiavana.accounting.features.cloudbackup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * The Cloud Backup preferences hold the passphrase sealed with a key that never leaves this phone, plus
 * this installation's folder grant. Restored elsewhere they would be useless at best, so they stay out of
 * Android's own cloud backup and device transfer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class CloudBackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun excludes(resource: Int): Set<String> {
        val parser = context.resources.getXml(resource)
        val found = mutableSetOf<String>()
        val path = ArrayDeque<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "exclude") {
                        found += "${path.lastOrNull().orEmpty()}/${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}"
                    }
                    path.addLast(parser.name)
                }
                XmlPullParser.END_TAG -> path.removeLast()
            }
        }
        return found
    }

    private val file = "sharedpref:${SharedPreferencesCloudBackupStore.PREFS_NAME}.xml"

    @Test
    fun `auto backup before Android 12 excludes the cloud backup preferences`() {
        assertTrue(excludes(R.xml.backup_rules).contains("full-backup-content/$file"))
    }

    @Test
    fun `cloud backup and device transfer exclude the cloud backup preferences`() {
        val rules = excludes(R.xml.data_extraction_rules)

        assertTrue(rules.contains("cloud-backup/$file"))
        assertTrue(rules.contains("device-transfer/$file"))
    }
}
