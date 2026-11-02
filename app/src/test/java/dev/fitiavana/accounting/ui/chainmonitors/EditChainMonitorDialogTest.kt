package dev.fitiavana.accounting.ui.chainmonitors

import android.view.ContextThemeWrapper
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitor
import dev.fitiavana.accounting.network.evm.Erc20Codec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class EditChainMonitorDialogTest {

    private val vault = "0xB99B6dF96d4d5448cC0a5B3e0ef7896df9507Cf5"
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Accounting)

    private var saved: MonitorForm? = null
    private var done: ((MonitorSaveError?) -> Unit)? = null

    private fun show(existing: ChainMonitor?): AlertDialog {
        EditChainMonitorDialog.show(context, existing) { form, onDone ->
            saved = form
            done = onDone
        }
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun AlertDialog.text(id: Int) = findViewById<TextInputEditText>(id)!!.text.toString()

    private fun AlertDialog.type(id: Int, value: String) = findViewById<TextInputEditText>(id)!!.setText(value)

    private fun AlertDialog.error(id: Int) = findViewById<TextInputLayout>(id)!!.error?.toString()

    private fun AlertDialog.save() = getButton(AlertDialog.BUTTON_POSITIVE).performClick()

    @Test
    fun `a new monitor starts with Base USDC, a zero threshold and 15 minutes`() {
        val dialog = show(null)

        assertEquals(Erc20Codec.BASE_USDC, dialog.text(R.id.input_monitor_token))
        assertEquals("0", dialog.text(R.id.input_monitor_threshold))
        assertEquals(R.id.radio_monitor_15, dialog.findViewById<RadioGroup>(R.id.group_monitor_interval)!!.checkedRadioButtonId)
        assertEquals("", dialog.text(R.id.input_monitor_label))
    }

    @Test
    fun `editing shows the monitor's values`() {
        val monitor = ChainMonitor("m", "Vault", vault, Erc20Codec.BASE_USDC, "USDC", 6, "2.5", 30, 1L)

        val dialog = show(monitor)

        assertEquals("Vault", dialog.text(R.id.input_monitor_label))
        assertEquals(vault, dialog.text(R.id.input_monitor_holder))
        assertEquals("2.5", dialog.text(R.id.input_monitor_threshold))
        assertEquals(R.id.radio_monitor_30, dialog.findViewById<RadioGroup>(R.id.group_monitor_interval)!!.checkedRadioButtonId)
    }

    @Test
    fun `save hands the typed form to the callback`() {
        val dialog = show(null)
        dialog.type(R.id.input_monitor_label, "Acres 40 available")
        dialog.type(R.id.input_monitor_holder, vault)
        dialog.findViewById<RadioGroup>(R.id.group_monitor_interval)!!.check(R.id.radio_monitor_30)

        dialog.save()

        assertEquals(
            MonitorForm("Acres 40 available", vault, Erc20Codec.BASE_USDC, "0", 30),
            saved
        )
    }

    @Test
    fun `the dialog stays open and busy until the save finishes, then closes on success`() {
        val dialog = show(null)
        dialog.save()

        assertTrue(dialog.isShowing)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)

        done!!.invoke(null)

        assertFalse(dialog.isShowing)
    }

    @Test
    fun `a field error is shown on its field and the dialog stays open`() {
        val dialog = show(null)
        dialog.save()

        done!!.invoke(MonitorSaveError.HOLDER)

        assertTrue(dialog.isShowing)
        assertNotNull(dialog.error(R.id.layout_monitor_holder))
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
    }

    @Test
    fun `each form error lands on the matching field`() {
        val fields = mapOf(
            MonitorSaveError.LABEL to R.id.layout_monitor_label,
            MonitorSaveError.HOLDER to R.id.layout_monitor_holder,
            MonitorSaveError.TOKEN to R.id.layout_monitor_token,
            MonitorSaveError.THRESHOLD to R.id.layout_monitor_threshold,
            MonitorSaveError.NOT_A_TOKEN to R.id.layout_monitor_token,
            MonitorSaveError.NO_NETWORK to R.id.layout_monitor_token,
            MonitorSaveError.FAILED to R.id.layout_monitor_token
        )
        fields.forEach { (error, field) ->
            val dialog = show(null)
            dialog.save()

            done!!.invoke(error)

            assertNotNull(error.name, dialog.error(field))
        }
    }

    @Test
    fun `typing clears the error of the field`() {
        val dialog = show(null)
        dialog.save()
        done!!.invoke(MonitorSaveError.HOLDER)

        dialog.type(R.id.input_monitor_holder, vault)

        assertNull(dialog.error(R.id.layout_monitor_holder))
    }
}
