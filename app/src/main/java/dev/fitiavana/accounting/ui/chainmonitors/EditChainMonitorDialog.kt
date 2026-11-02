package dev.fitiavana.accounting.ui.chainmonitors

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitor
import dev.fitiavana.accounting.network.evm.Erc20Codec

/**
 * Dialog to add a monitor, or edit [existing]. [onSave] gets what was typed and a callback to call when the
 * save is over: with an error the dialog stays open and shows it on the matching field, with null it closes.
 * The dialog is busy (Save disabled) in between, as saving reads the token from the chain.
 */
object EditChainMonitorDialog {

    fun show(
        context: Context,
        existing: ChainMonitor?,
        onSave: (form: MonitorForm, onDone: (MonitorSaveError?) -> Unit) -> Unit
    ) {
        val content = LayoutInflater.from(context).inflate(R.layout.dialog_edit_chain_monitor, null)
        val fields = Field.values().associateWith { Binding(content, it) }
        val intervalGroup = content.findViewById<RadioGroup>(R.id.group_monitor_interval)

        fields.getValue(Field.LABEL).input.setText(existing?.label.orEmpty())
        fields.getValue(Field.HOLDER).input.setText(existing?.holderAddress.orEmpty())
        fields.getValue(Field.TOKEN).input.setText(existing?.tokenAddress ?: Erc20Codec.BASE_USDC)
        fields.getValue(Field.THRESHOLD).input.setText(existing?.threshold ?: "0")
        if (existing?.intervalMinutes == 30) intervalGroup.check(R.id.radio_monitor_30)
        fields.values.forEach { it.clearErrorOnTyping() }

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (existing == null) R.string.dialog_edit_chain_monitor_title_new else R.string.dialog_edit_chain_monitor_title_edit)
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
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        // Set after show() so a failed save keeps the dialog open instead of dismissing it.
        save.setOnClickListener {
            val form = MonitorForm(
                label = fields.getValue(Field.LABEL).text(),
                holder = fields.getValue(Field.HOLDER).text(),
                token = fields.getValue(Field.TOKEN).text(),
                threshold = fields.getValue(Field.THRESHOLD).text(),
                intervalMinutes = if (intervalGroup.checkedRadioButtonId == R.id.radio_monitor_30) 30 else 15
            )
            save.isEnabled = false
            onSave(form) { error ->
                save.isEnabled = true
                if (error == null) {
                    dialog.dismiss()
                } else {
                    fields.getValue(error.field()).layout.error = context.getString(error.message())
                }
            }
        }
    }

    private enum class Field(val layoutId: Int, val inputId: Int) {
        LABEL(R.id.layout_monitor_label, R.id.input_monitor_label),
        HOLDER(R.id.layout_monitor_holder, R.id.input_monitor_holder),
        TOKEN(R.id.layout_monitor_token, R.id.input_monitor_token),
        THRESHOLD(R.id.layout_monitor_threshold, R.id.input_monitor_threshold)
    }

    private class Binding(content: android.view.View, field: Field) {
        val layout: TextInputLayout = content.findViewById(field.layoutId)
        val input: TextInputEditText = content.findViewById(field.inputId)

        fun text() = input.text.toString()

        fun clearErrorOnTyping() {
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    layout.error = null
                }
            })
        }
    }

    /** The field an error is shown on: problems reading the token are shown on the token field. */
    private fun MonitorSaveError.field() = when (this) {
        MonitorSaveError.LABEL -> Field.LABEL
        MonitorSaveError.HOLDER -> Field.HOLDER
        MonitorSaveError.THRESHOLD -> Field.THRESHOLD
        MonitorSaveError.TOKEN, MonitorSaveError.NOT_A_TOKEN, MonitorSaveError.NO_NETWORK, MonitorSaveError.FAILED -> Field.TOKEN
    }

    private fun MonitorSaveError.message() = when (this) {
        MonitorSaveError.LABEL -> R.string.error_chain_monitor_label
        MonitorSaveError.HOLDER -> R.string.error_chain_monitor_holder
        MonitorSaveError.TOKEN -> R.string.error_chain_monitor_token
        MonitorSaveError.THRESHOLD -> R.string.error_chain_monitor_threshold
        MonitorSaveError.NOT_A_TOKEN -> R.string.error_chain_monitor_not_a_token
        MonitorSaveError.NO_NETWORK -> R.string.error_chain_monitor_no_network
        MonitorSaveError.FAILED -> R.string.error_chain_monitor_failed
    }
}
