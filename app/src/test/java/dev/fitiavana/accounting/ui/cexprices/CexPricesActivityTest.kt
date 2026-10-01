package dev.fitiavana.accounting.ui.cexprices

import android.content.Context
import android.os.Looper
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.instruments.Instrument
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class CexPricesActivityTest {

    private fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms")
    }

    private fun Spinner.items(): List<String> =
        (0 until adapter.count).map { adapter.getItem(it).toString() }

    @Test
    fun `base and quote spinners list only cryptocurrency instruments`() {
        val repo = AppContainer.getInstance(ApplicationProvider.getApplicationContext<Context>()).instrumentRepository
        // A single crypto keeps base == quote, so no network fetch is triggered.
        val seeder = Thread {
            repo.insert(Instrument("ZZCRYPTO", "", Instrument.TYPE_CRYPTOCURRENCY))
            repo.insert(Instrument("ZZFIAT", "", "currency"))
        }
        seeder.start()
        seeder.join()

        val activity = Robolectric.buildActivity(CexPricesActivity::class.java).setup().get()
        val base = activity.findViewById<Spinner>(R.id.spinner_base)
        val quote = activity.findViewById<Spinner>(R.id.spinner_quote)

        waitUntil { base.items().contains("ZZCRYPTO") }

        assertTrue(quote.items().contains("ZZCRYPTO"))
        assertFalse(base.items().contains("ZZFIAT"))
        assertFalse(quote.items().contains("ZZFIAT"))
    }
}
