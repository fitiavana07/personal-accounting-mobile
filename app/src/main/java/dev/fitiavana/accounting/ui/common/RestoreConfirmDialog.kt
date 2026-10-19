package dev.fitiavana.accounting.ui.common

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import dev.fitiavana.accounting.R

/** The "erase everything and restore from this backup?" confirmation, shared by every restore entry point. */
object RestoreConfirmDialog {

    fun show(context: Context, fileName: String, onConfirm: () -> Unit) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.restore_confirm_title)
            .setMessage(context.getString(R.string.restore_confirm_message, fileName))
            .setPositiveButton(R.string.action_restore) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(context, R.color.loss))
    }
}
