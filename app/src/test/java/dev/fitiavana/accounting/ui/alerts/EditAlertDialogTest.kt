package dev.fitiavana.accounting.ui.alerts

import android.view.ContextThemeWrapper
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class EditAlertDialogTest {

    private val choices = listOf(AccountChoice("a", "Alpha"), AccountChoice("b", "Beta"))

    private fun showNew(preselectAccountId: String?): Spinner {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Accounting)
        EditAlertDialog.show(context, choices, null, preselectAccountId) { _, _, _ -> null }
        return ShadowDialog.getLatestDialog().findViewById(R.id.spinner_alert_account)
    }

    @Test
    fun `a new alert preselects the given account`() {
        assertEquals(1, showNew("b").selectedItemPosition)
    }

    @Test
    fun `a new alert starts on the first account without a preselection`() {
        assertEquals(0, showNew(null).selectedItemPosition)
    }
}
