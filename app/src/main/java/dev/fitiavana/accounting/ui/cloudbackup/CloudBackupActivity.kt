package dev.fitiavana.accounting.ui.cloudbackup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.BuildConfig
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupError
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupFile
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupOutcome
import dev.fitiavana.accounting.features.cloudbackup.CloudBackupPolicy
import dev.fitiavana.accounting.features.cloudbackup.CloudRestoreResult
import dev.fitiavana.accounting.ui.common.RestoreConfirmDialog
import dev.fitiavana.accounting.ui.common.RestoreResultPresenter
import dev.fitiavana.accounting.ui.common.UiUtils
import java.text.DateFormat
import java.util.Date

/**
 * Daily encrypted backups to a Google Drive folder the user picks with the Storage Access Framework,
 * and restoring from them. Needs Android 6.0 (see [dev.fitiavana.accounting.features.cloudbackup.isCloudBackupSupported]);
 * below it the screen only explains that.
 */
class CloudBackupActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, CloudBackupActivity::class.java)
    }

    private lateinit var viewModel: CloudBackupViewModel
    private var pickFolder: ActivityResultLauncher<Uri?>? = null
    private lateinit var askNotificationPermission: ActivityResultLauncher<String>
    private var backupsDialog: AlertDialog? = null

    /** The backup being restored while the user is asked for its passphrase. */
    private var restoringFile: CloudBackupFile? = null
    private var passphraseDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_backup)

        UiUtils.setupActionBar(this)
        title = getString(R.string.action_cloud_backup)

        val container = AppContainer.getInstance(this)
        viewModel = ViewModelProvider(
            this,
            CloudBackupViewModelFactory(container.cloudBackupRepository, container.cloudBackupManager)
        ).get(CloudBackupViewModel::class.java)

        if (Build.VERSION.SDK_INT >= 23 && viewModel.state.value!!.supported) pickFolder = registerFolderPicker()
        askNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

        val launchPicker = { pickFolder?.launch(null) }
        findViewById<View>(R.id.button_cloud_choose_folder).setOnClickListener { launchPicker() }
        findViewById<View>(R.id.button_cloud_change_folder).setOnClickListener { launchPicker() }
        findViewById<View>(R.id.button_cloud_disconnect).setOnClickListener { viewModel.disconnect() }
        findViewById<View>(R.id.button_cloud_restore).setOnClickListener { viewModel.loadBackups() }
        findViewById<View>(R.id.button_cloud_backup_now).setOnClickListener { viewModel.backUpNow() }

        val passphraseInput = findViewById<EditText>(R.id.edit_cloud_passphrase)
        findViewById<View>(R.id.button_cloud_save_passphrase).setOnClickListener {
            viewModel.savePassphrase(passphraseInput.text.toString())
        }
        findViewById<View>(R.id.button_cloud_cancel_passphrase).setOnClickListener {
            passphraseInput.text.clear()
            viewModel.cancelChangingPassphrase()
        }
        findViewById<View>(R.id.button_cloud_change_passphrase).setOnClickListener { viewModel.startChangingPassphrase() }
        passphraseInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = viewModel.passphraseEdited()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        findViewById<SwitchCompat>(R.id.switch_cloud_enabled).setOnCheckedChangeListener { _, checked ->
            viewModel.setEnabled(checked)
            if (checked) requestNotificationPermission()
        }

        findViewById<TextView>(R.id.text_cloud_retention).text =
            getString(R.string.cloud_backup_retention, CloudBackupPolicy.KEPT_BACKUPS)
        findViewById<View>(R.id.text_cloud_debug_hint).visibility = visible(BuildConfig.DEBUG)

        viewModel.state.observe(this) { render(it) }
    }

    override fun onResume() {
        super.onResume()
        // A background backup may have run since the screen was last shown.
        viewModel.refresh()
    }

    override fun onDestroy() {
        dismissBackupsDialog()
        passphraseDialog?.dismiss()
        super.onDestroy()
    }

    /** The folder picker is API 21+; it is only registered where the feature is supported (API 23). */
    @RequiresApi(23)
    private fun registerFolderPicker(): ActivityResultLauncher<Uri?> =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) connect(uri)
        }

    private fun connect(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // The provider did not allow keeping the access; connecting then fails the same way below.
        }
        viewModel.connect(uri.toString())
    }

    /** A failure notification needs the permission from Android 13; without it the screen still shows the status. */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) askNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun render(state: CloudBackupScreenState) {
        val connected = state.folderName != null
        val ready = state.supported && connected && state.hasPassphrase
        val busy = state.backingUp || state.restoring

        findViewById<View>(R.id.text_cloud_unsupported).visibility = visible(!state.supported)
        findViewById<View>(R.id.button_cloud_choose_folder).apply {
            visibility = visible(!connected)
            isEnabled = state.supported
        }
        findViewById<View>(R.id.group_cloud_folder).visibility = visible(connected)
        findViewById<TextView>(R.id.text_cloud_folder_name).text = state.folderName.orEmpty()

        val passphraseInput = findViewById<EditText>(R.id.edit_cloud_passphrase)
        findViewById<View>(R.id.group_cloud_passphrase_form).visibility = visible(state.showPassphraseForm)
        findViewById<View>(R.id.group_cloud_passphrase_set).visibility = visible(!state.showPassphraseForm)
        findViewById<View>(R.id.button_cloud_cancel_passphrase).visibility = visible(state.hasPassphrase)
        findViewById<View>(R.id.text_cloud_passphrase_error).visibility = visible(state.passphraseTooShort)
        findViewById<View>(R.id.button_cloud_save_passphrase).isEnabled = state.supported
        passphraseInput.isEnabled = state.supported
        if (!state.showPassphraseForm) passphraseInput.text.clear()

        findViewById<SwitchCompat>(R.id.switch_cloud_enabled).apply {
            isEnabled = ready
            // Moving the switch from here must not look like the user flipping it.
            setOnCheckedChangeListener(null)
            isChecked = state.enabled
            setOnCheckedChangeListener { _, checked ->
                viewModel.setEnabled(checked)
                if (checked) requestNotificationPermission()
            }
        }

        findViewById<TextView>(R.id.text_cloud_status).text = when {
            state.backingUp -> getString(R.string.cloud_backup_working)
            state.lastSuccessAt == null -> getString(R.string.cloud_backup_status_never)
            else -> getString(
                R.string.cloud_backup_last_success,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.lastSuccessAt))
            )
        }
        findViewById<TextView>(R.id.text_cloud_error).apply {
            val error = state.lastError
            visibility = visible(error != null)
            text = error?.let { getString(errorText(it)) }.orEmpty()
        }
        findViewById<View>(R.id.button_cloud_backup_now).isEnabled = ready && !busy
        findViewById<View>(R.id.button_cloud_restore).isEnabled = state.supported && connected && !busy

        if (state.connectFailed) {
            Toast.makeText(this, R.string.cloud_backup_connect_failed, Toast.LENGTH_LONG).show()
            viewModel.connectFailureShown()
        }
        state.backupResult?.let { showBackupResult(it) }
        state.restoreResult?.let { showRestoreResult(it) }
        showBackups(state.backups)
    }

    private fun errorText(error: CloudBackupError) = when (error) {
        CloudBackupError.OFFLINE -> R.string.cloud_backup_error_offline
        CloudBackupError.NOT_CONFIGURED -> R.string.cloud_backup_error_not_configured
        CloudBackupError.FOLDER -> R.string.cloud_backup_error_folder
        CloudBackupError.UNEXPECTED -> R.string.cloud_backup_error_unexpected
    }

    private fun showBackupResult(outcome: CloudBackupOutcome) {
        val message = when (outcome) {
            is CloudBackupOutcome.Success -> R.string.cloud_backup_done
            CloudBackupOutcome.Offline -> R.string.cloud_backup_error_offline
            CloudBackupOutcome.NotConfigured -> R.string.cloud_backup_error_not_configured
            CloudBackupOutcome.Failed -> R.string.cloud_backup_error_folder
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        viewModel.backupResultShown()
    }

    private fun showRestoreResult(result: CloudRestoreResult) {
        viewModel.restoreResultShown()
        when (result) {
            is CloudRestoreResult.Restored -> RestoreResultPresenter.show(this, result.result)
            CloudRestoreResult.NeedsPassphrase -> askPassphrase(R.string.cloud_backup_passphrase_needed_message)
            CloudRestoreResult.WrongPassphrase -> askPassphrase(R.string.cloud_backup_passphrase_wrong_message)
            CloudRestoreResult.NotABackup -> toast(R.string.cloud_backup_not_a_backup)
            CloudRestoreResult.UnsupportedFormat -> toast(R.string.cloud_backup_unsupported_format)
            CloudRestoreResult.Unreachable -> toast(R.string.cloud_backup_restore_unreachable)
        }
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    /** Asks for the passphrase of the backup being restored; it is used for this restore only. */
    private fun askPassphrase(message: Int) {
        val file = restoringFile ?: return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        passphraseDialog?.dismiss()
        passphraseDialog = AlertDialog.Builder(this)
            .setTitle(R.string.cloud_backup_passphrase_needed_title)
            .setMessage(message)
            .setView(input)
            .setPositiveButton(R.string.action_restore) { _, _ ->
                val typed = input.text.toString()
                if (typed.isNotEmpty()) viewModel.restore(file, typed)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBackups(backups: BackupListState) {
        dismissBackupsDialog()
        val builder = AlertDialog.Builder(this).setTitle(R.string.cloud_backup_restore_title)
        when (backups) {
            BackupListState.Hidden -> return
            BackupListState.Loading -> builder.setMessage(R.string.cloud_backup_restore_loading)
            BackupListState.Failed -> builder.setMessage(R.string.cloud_backup_restore_failed)
            is BackupListState.Loaded ->
                if (backups.files.isEmpty()) {
                    builder.setMessage(R.string.cloud_backup_restore_empty)
                } else {
                    builder.setItems(backups.files.map(::describe).toTypedArray()) { _, which ->
                        confirmRestore(backups.files[which])
                    }
                }
        }
        backupsDialog = builder
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { viewModel.dismissBackups() }
            .show()
    }

    /** Closes the list without telling the view model: it is being replaced, not dismissed by the user. */
    private fun dismissBackupsDialog() {
        backupsDialog?.setOnDismissListener(null)
        backupsDialog?.dismiss()
        backupsDialog = null
    }

    private fun describe(file: CloudBackupFile): String {
        val date = if (file.modifiedAt > 0) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.modifiedAt))
        } else {
            "-"
        }
        return getString(R.string.cloud_backup_file_item, file.name, date, Formatter.formatShortFileSize(this, file.sizeBytes))
    }

    private fun confirmRestore(file: CloudBackupFile) {
        RestoreConfirmDialog.show(this, file.name) {
            restoringFile = file
            viewModel.restore(file, null)
        }
    }

    private fun visible(show: Boolean) = if (show) View.VISIBLE else View.GONE
}
