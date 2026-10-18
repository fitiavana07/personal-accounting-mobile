package dev.fitiavana.accounting.ui.common

import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class MainAmountEmphasisTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Accounting
    )

    @Test
    fun `colors only the main amount and keeps the text`() {
        val line = "New balance: 8.5 USD · 340,000 Ar"

        val result = emphasizeMainAmount(context, line, "8.5 USD") as Spanned

        assertEquals(line, result.toString())
        val span = result.getSpans(0, result.length, ForegroundColorSpan::class.java).single()
        assertEquals(line.indexOf("8.5 USD"), result.getSpanStart(span))
        assertEquals(line.indexOf("8.5 USD") + "8.5 USD".length, result.getSpanEnd(span))
        assertEquals(ContextCompat.getColor(context, R.color.balance_main), span.foregroundColor)
    }

    @Test
    fun `leaves the text plain when the main amount is not in it`() {
        val result = emphasizeMainAmount(context, "Balance: 1 Ar", "2 USD") as Spanned

        assertFalse(result.getSpans(0, result.length, ForegroundColorSpan::class.java).isNotEmpty())
    }
}
