package dev.fitiavana.accounting.ui.sync

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

/**
 * Keeps the user's devices in step over the local Wi-Fi: the shared passphrase, the auto-sync switch,
 * and the devices found. Sync itself runs for as long as the app is on screen (see AccountingApplication).
 */
class SyncActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, SyncActivity::class.java)
    }

    private lateinit var viewModel: SyncViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)

        UiUtils.setupActionBar(this)
        title = getString(R.string.sync)

        val container = AppContainer.getInstance(this)
        viewModel = ViewModelProvider(
            this,
            SyncViewModelFactory(container.syncCoordinator, container.syncSettingsStore)
        ).get(SyncViewModel::class.java)

        val passphraseInput = findViewById<EditText>(R.id.edit_sync_passphrase)
        val autoSwitch = findViewById<SwitchCompat>(R.id.switch_sync_auto)
        val peersAdapter = SyncPeersAdapter(viewModel::syncWith)
        findViewById<RecyclerView>(R.id.recycler_sync_peers).apply {
            layoutManager = LinearLayoutManager(this@SyncActivity)
            adapter = peersAdapter
        }

        val save = { viewModel.savePassphrase(passphraseInput.text.toString()) }
        findViewById<View>(R.id.button_sync_save_passphrase).setOnClickListener { save() }
        passphraseInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) save() }
        }
        passphraseInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = viewModel.passphraseEdited()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        findViewById<View>(R.id.button_sync_cancel_passphrase).setOnClickListener {
            passphraseInput.text.clear()
            viewModel.cancelChangingPassphrase()
        }
        findViewById<View>(R.id.button_sync_change_passphrase).setOnClickListener { viewModel.startChangingPassphrase() }
        findViewById<View>(R.id.button_sync_remove_passphrase).setOnClickListener { confirmRemovePassphrase() }
        findViewById<View>(R.id.button_sync_all).setOnClickListener { viewModel.syncAll() }
        findViewById<View>(R.id.button_sync_refresh).setOnClickListener { viewModel.refresh() }

        val onAutoChanged = { _: Any, checked: Boolean -> viewModel.setAutoSync(checked) }
        autoSwitch.setOnCheckedChangeListener(onAutoChanged)

        viewModel.state.observe(this) { state ->
            findViewById<TextView>(R.id.text_sync_device_name).text = state.deviceName

            findViewById<View>(R.id.group_passphrase_form).visibility = visible(state.showPassphraseForm)
            findViewById<View>(R.id.group_passphrase_set).visibility = visible(!state.showPassphraseForm)
            findViewById<View>(R.id.button_sync_cancel_passphrase).visibility =
                visible(state.showPassphraseForm && state.hasPassphrase)
            findViewById<View>(R.id.text_sync_passphrase_error).visibility = visible(state.passphraseTooShort)
            if (!state.showPassphraseForm && passphraseInput.text.isNotEmpty()) passphraseInput.text.clear()

            // Setting the switch from the state must not look like the user flipping it.
            autoSwitch.setOnCheckedChangeListener(null)
            autoSwitch.isChecked = state.autoSync
            autoSwitch.setOnCheckedChangeListener(onAutoChanged)

            findViewById<View>(R.id.button_sync_all).isEnabled = state.hasPassphrase && state.peers.isNotEmpty()
            findViewById<View>(R.id.button_sync_refresh).isEnabled = state.hasPassphrase
            findViewById<TextView>(R.id.text_sync_network_error).apply {
                visibility = visible(state.networkError != null)
                text = state.networkError?.let { getString(R.string.sync_network_error, it) }.orEmpty()
            }
            findViewById<TextView>(R.id.text_sync_empty).apply {
                visibility = visible(state.peers.isEmpty() && state.networkError == null)
                setText(if (state.hasPassphrase) R.string.sync_empty else R.string.sync_empty_no_passphrase)
            }
            peersAdapter.submit(state.peers)
            findViewById<TextView>(R.id.text_sync_last_synced).text =
                SyncStatusFormatter.lastSynced(this, state.lastSyncedAt)
        }
    }

    private fun visible(show: Boolean) = if (show) View.VISIBLE else View.GONE

    /** Destructive: ask first, and colour the confirming button red. */
    private fun confirmRemovePassphrase() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.sync_remove_title)
            .setMessage(R.string.sync_remove_message)
            .setPositiveButton(R.string.sync_remove_passphrase) { _, _ -> viewModel.removePassphrase() }
            .setNegativeButton(R.string.sync_cancel, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.loss))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
