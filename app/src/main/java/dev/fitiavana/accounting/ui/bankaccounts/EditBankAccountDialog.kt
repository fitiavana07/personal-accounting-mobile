package dev.fitiavana.accounting.ui.bankaccounts

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.fitiavana.accounting.R

/**
 * Dialog to name a new bank account, or rename [existingName]. [onSave] gets the typed name and a callback to
 * call once saved: with false the dialog stays open and shows an error, with true it closes.
 */
object EditBankAccountDialog {

    fun show(context: Context, existingName: String?, onSave: (name: String, onDone: (Boolean) -> Unit) -> Unit) {
        val content = LayoutInflater.from(context).inflate(R.layout.dialog_bank_account_name, null)
        val layout = content.findViewById<TextInputLayout>(R.id.layout_bank_account_name)
        val input = content.findViewById<TextInputEditText>(R.id.input_bank_account_name)
        input.setText(existingName.orEmpty())
        input.setSelection(input.text?.length ?: 0)

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (existingName == null) R.string.dialog_bank_account_title_new else R.string.dialog_bank_account_title_edit)
            .setView(content)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        // Cancel is muted so the primary action stands out. TypedArray is only Closeable from API 31.
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(attrs.getColorStateList(0))
        attrs.recycle()
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        // Set after show() so an invalid name keeps the dialog open instead of dismissing it.
        save.setOnClickListener {
            layout.error = null
            save.isEnabled = false
            onSave(input.text.toString()) { saved ->
                save.isEnabled = true
                if (saved) dialog.dismiss() else layout.error = context.getString(R.string.error_bank_account_name)
            }
        }
    }
}
