package dev.fitiavana.accounting.ui.bankaccounts

import android.widget.EditText
import dev.fitiavana.accounting.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AddBankAccountTransactionAmountFormattingTest {

    @Test
    fun `the amount input shows thousands separators as the user types`() {
        val intent = AddBankAccountTransactionActivity.intent(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(), "acc-1"
        )
        val activity = Robolectric.buildActivity(AddBankAccountTransactionActivity::class.java, intent)
            .setup().get()

        val input = activity.findViewById<EditText>(R.id.input_bank_amount)
        input.setText("1234567")

        assertEquals("1,234,567", input.text.toString())
    }
}
