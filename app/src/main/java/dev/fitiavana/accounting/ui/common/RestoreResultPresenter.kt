package dev.fitiavana.accounting.ui.common

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.backup.RestoreResult

/** Tells the user how a restore went, whichever screen started it. Call on the main thread. */
object RestoreResultPresenter {

    fun show(context: Context, result: RestoreResult) {
        when (result) {
            is RestoreResult.Success -> Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show()
            is RestoreResult.SuccessFromOlderVersion -> showOlderVersionWarning(context, result.backupVersion, result.currentVersion)
            is RestoreResult.SchemaMismatch -> Toast.makeText(
                context,
                context.getString(R.string.restore_schema_mismatch, result.backupVersion, result.currentVersion),
                Toast.LENGTH_LONG
            ).show()
            is RestoreResult.Error -> Toast.makeText(
                context, context.getString(R.string.restore_failed, result.message), Toast.LENGTH_LONG
            ).show()
        }
    }

    /** A dialog rather than a toast: the message is long and a toast truncates it. */
    fun showOlderVersionWarning(context: Context, backupVersion: Int, currentVersion: Int) {
        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_restore_title)
            .setMessage(context.getString(R.string.restore_success_older_version, backupVersion, currentVersion))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
