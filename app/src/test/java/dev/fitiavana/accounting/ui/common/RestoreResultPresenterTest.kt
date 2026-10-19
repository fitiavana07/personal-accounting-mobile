package dev.fitiavana.accounting.ui.common

import android.content.Context
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.backup.RestoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class RestoreResultPresenterTest {
    private val activity: AppCompatActivity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get().also {
        it.setTheme(R.style.Theme_Accounting)
    }
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a success is a short toast`() {
        RestoreResultPresenter.show(activity, RestoreResult.Success)

        assertEquals(context.getString(R.string.restore_success), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `an older backup shows the full warning in a dialog`() {
        RestoreResultPresenter.show(activity, RestoreResult.SuccessFromOlderVersion(21, 22))
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        assertEquals(
            context.getString(R.string.restore_success_older_version, 21, 22),
            dialog.findViewById<TextView>(android.R.id.message)!!.text.toString()
        )
    }

    @Test
    fun `a newer backup is refused with a toast`() {
        RestoreResultPresenter.show(activity, RestoreResult.SchemaMismatch(30, 22))

        assertEquals(context.getString(R.string.restore_schema_mismatch, 30, 22), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `an error says why`() {
        RestoreResultPresenter.show(activity, RestoreResult.Error("bad json"))

        assertEquals(context.getString(R.string.restore_failed, "bad json"), ShadowToast.getTextOfLatestToast())
    }
}
