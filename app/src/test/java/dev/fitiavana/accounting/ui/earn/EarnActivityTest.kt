package dev.fitiavana.accounting.ui.earn

import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class EarnActivityTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms")
    }

    private fun launch() = Robolectric.buildActivity(EarnActivity::class.java, EarnActivity.intent(context)).setup().get()

    /** The app database is shared by every test in the run, so remove Earn accounts left by other tests. */
    private fun removeEarnAccounts() {
        Thread {
            val repository = AppContainer.getInstance(context).accountRepository
            repository.getAllSync().filter { it.aprPercent != null }.forEach { repository.delete(it) }
        }.apply { start(); join() }
    }

    @Test
    fun `the title is Earn`() {
        assertEquals(context.getString(R.string.title_earn), launch().title.toString())
    }

    @Test
    fun `shows a hint when no account has an APR`() {
        removeEarnAccounts()
        val activity = launch()
        shadowOf(Looper.getMainLooper()).idle()

        waitUntil { activity.findViewById<View>(R.id.text_earn_empty).visibility == View.VISIBLE }

        assertEquals(View.GONE, activity.findViewById<View>(R.id.recycler_earn).visibility)
    }

    @Test
    fun `lists the Earn accounts with a totals row`() {
        Thread {
            AppContainer.getInstance(context).accountRepository
                .insert(Account(id = "earn_act", name = "Earn Activity Account", type = "asset", aprPercent = 8.0))
        }.apply { start(); join() }
        val activity = launch()

        val recycler = activity.findViewById<RecyclerView>(R.id.recycler_earn)
        waitUntil { (recycler.adapter?.itemCount ?: 0) >= 2 }

        assertEquals(View.VISIBLE, recycler.visibility)
        assertEquals(View.GONE, activity.findViewById<TextView>(R.id.text_earn_empty).visibility)
    }

    @Test
    fun `up navigation closes the screen`() {
        val activity = launch()

        activity.onSupportNavigateUp()

        assertEquals(true, activity.isFinishing)
    }
}
