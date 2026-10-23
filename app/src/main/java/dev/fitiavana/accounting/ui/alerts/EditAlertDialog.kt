package dev.fitiavana.accounting.ui.alerts

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.alerts.AlertTypes
import java.math.BigDecimal

/**
 * Dialog to add an alert (on [preselectAccountId] when given), or edit [existing]. [onSave] gets the chosen account, type and the threshold as
 * typed, and returns what is wrong with them (the dialog then stays open) or null once saving started.
 */
object EditAlertDialog {

    fun show(
        context: Context,
        choices: List<AccountChoice>,
        existing: AlertItem?,
        preselectAccountId: String? = null,
        onSave: (accountId: String?, type: String, thresholdText: String) -> AlertSaveError?
    ) {
        // An alert whose account no longer qualifies for Home must still show its own account.
        val accounts = if (existing != null && choices.none { it.id == existing.alert.accountId }) {
            choices + AccountChoice(existing.alert.accountId, existing.accountName)
        } else {
            choices
        }
        val content = LayoutInflater.from(context).inflate(R.layout.dialog_edit_alert, null)
        val spinner = content.findViewById<Spinner>(R.id.spinner_alert_account)
        val typeGroup = content.findViewById<RadioGroup>(R.id.group_alert_type)
        val layout = content.findViewById<TextInputLayout>(R.id.layout_alert_threshold)
        val input = content.findViewById<TextInputEditText>(R.id.input_alert_threshold)

        spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, accounts.map { it.name })
        if (existing == null && preselectAccountId != null) {
            spinner.setSelection(accounts.indexOfFirst { it.id == preselectAccountId }.coerceAtLeast(0))
        }
        if (existing != null) {
            spinner.setSelection(accounts.indexOfFirst { it.id == existing.alert.accountId }.coerceAtLeast(0))
            typeGroup.check(
                if (existing.alert.type == AlertTypes.CROSSING_ABOVE) R.id.radio_alert_above else R.id.radio_alert_below
            )
            input.setText(BigDecimal.valueOf(existing.alert.thresholdPercent).stripTrailingZeros().toPlainString())
            input.setSelection(input.text?.length ?: 0)
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                layout.error = null
            }
        })

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (existing == null) R.string.dialog_edit_alert_title_new else R.string.dialog_edit_alert_title_edit)
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
        // Set after show() so an invalid form keeps the dialog open instead of dismissing it.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val type = if (typeGroup.checkedRadioButtonId == R.id.radio_alert_below) {
                AlertTypes.CROSSING_BELOW
            } else {
                AlertTypes.CROSSING_ABOVE
            }
            val accountId = accounts.getOrNull(spinner.selectedItemPosition)?.id
            when (onSave(accountId, type, input.text.toString())) {
                null -> dialog.dismiss()
                AlertSaveError.NO_ACCOUNT -> layout.error = context.getString(R.string.error_alert_no_account)
                AlertSaveError.INVALID_THRESHOLD -> layout.error = context.getString(R.string.error_alert_threshold)
            }
        }
    }
}
