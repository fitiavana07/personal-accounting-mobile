package dev.fitiavana.accounting.ui.common

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.fitiavana.accounting.R

/**
 * Dialog to change the yearly interest rate (APR, in percent) of the account named [accountName], which is
 * the title so it is clear what is being edited. Only positive rates can be saved.
 */
object EditAprDialog {

    fun show(context: Context, accountName: String, currentApr: Double, onSave: (Double) -> Unit) {
        val content = LayoutInflater.from(context).inflate(R.layout.dialog_edit_apr, null)
        val layout = content.findViewById<TextInputLayout>(R.id.layout_apr)
        val input = content.findViewById<TextInputEditText>(R.id.input_apr)
        input.setText(TransactionDisplay.formatApr(currentApr))
        input.setSelection(input.text?.length ?: 0)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                layout.error = null
            }
        })
        val dialog = AlertDialog.Builder(context)
            .setTitle(accountName)
            .setView(content)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        // Save keeps the accent colour; Cancel is muted so the primary action stands out.
        // TypedArray is only Closeable from API 31, so recycle it by hand.
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(attrs.getColorStateList(0))
        attrs.recycle()
        // Set after show() so an invalid rate keeps the dialog open instead of dismissing it.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val apr = TransactionDisplay.parseApr(input.text.toString())
            if (apr != null && apr > 0.0) {
                onSave(apr)
                dialog.dismiss()
            } else {
                layout.error = context.getString(R.string.error_invalid_apr)
            }
        }
    }
}
