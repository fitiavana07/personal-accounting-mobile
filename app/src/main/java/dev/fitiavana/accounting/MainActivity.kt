package dev.fitiavana.accounting

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.fitiavana.accounting.features.backup.BackupRepository
import dev.fitiavana.accounting.ui.common.RestoreResultPresenter
import dev.fitiavana.accounting.ui.common.UiUtils
import dev.fitiavana.accounting.ui.accounts.AccountsFragment
import dev.fitiavana.accounting.ui.backup.AutoBackupViewModel
import dev.fitiavana.accounting.ui.backup.AutoBackupViewModelFactory
import dev.fitiavana.accounting.ui.balances.BalancesActivity
import dev.fitiavana.accounting.ui.changelog.ChangelogActivity
import dev.fitiavana.accounting.ui.home.HomeFragment
import dev.fitiavana.accounting.ui.instruments.InstrumentsActivity
import dev.fitiavana.accounting.ui.reports.ReportsFragment
import dev.fitiavana.accounting.ui.cloudbackup.CloudBackupActivity
import dev.fitiavana.accounting.ui.cloudbackup.CloudBackupViewModel
import dev.fitiavana.accounting.ui.cloudbackup.CloudBackupViewModelFactory
import dev.fitiavana.accounting.ui.sync.SyncActivity
import dev.fitiavana.accounting.ui.transactions.AddTransactionActivity
import dev.fitiavana.accounting.ui.transactions.TransactionsFragment
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var backupRepository: BackupRepository
    private lateinit var autoBackupViewModel: AutoBackupViewModel
    private var pendingRestoreUri: Uri? = null
    private var operationInProgress = false

    private val createBackupDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) performBackup(uri)
        }

    private val openRestoreDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) confirmRestore(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val container = AppContainer.getInstance(this)
        backupRepository = container.backupRepository
        autoBackupViewModel = ViewModelProvider(this, AutoBackupViewModelFactory(container.autoBackupManager))
            .get(AutoBackupViewModel::class.java)
        // Catch up on a daily backup whose alarm never fired (e.g. force-stopped app), and re-arm it.
        if (savedInstanceState == null) autoBackupViewModel.runCatchUp()
        // The same for the daily cloud backup, which has its own alarm.
        if (savedInstanceState == null) {
            ViewModelProvider(this, CloudBackupViewModelFactory(container.cloudBackupRepository, container.cloudBackupManager))
                .get(CloudBackupViewModel::class.java)
                .runCatchUp()
        }

        UiUtils.setupActionBar(
            this,
            displayHomeAsUp = false,
            padForNavigationBar = false
        )
        supportActionBar?.title =
            getString(R.string.app_name_with_version, BuildConfig.VERSION_NAME)

        val bottomNav =
            findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
                val navBar =
                    insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    navBar.bottom
                )
                insets
            }
        }

        findViewById<View>(R.id.fab_add_transaction).setOnClickListener {
            startActivity(AddTransactionActivity.intent(this))
        }
        // Keep the button's center on the bar's top edge, whatever the bar's height (insets included).
        val addButtonContainer = findViewById<View>(R.id.add_transaction_container)
        bottomNav.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val params = addButtonContainer.layoutParams as FrameLayout.LayoutParams
            val margin = (bottom - top) - addButtonContainer.measuredHeight / 2
            if (params.bottomMargin != margin) {
                params.bottomMargin = margin
                addButtonContainer.layoutParams = params
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
            bottomNav.selectedItemId = R.id.nav_home
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_reports -> ReportsFragment()
                R.id.nav_transactions -> TransactionsFragment()
                R.id.nav_accounts -> AccountsFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_backup)?.isEnabled = !operationInProgress
        menu.findItem(R.id.action_restore)?.isEnabled = !operationInProgress
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (operationInProgress) return true
        when (item.itemId) {
            R.id.action_backup -> {
                val filename = getString(
                    R.string.backup_default_filename,
                    backupFilenameDateFormat.format(Date())
                )
                createBackupDocument.launch(filename)
                return true
            }

            R.id.action_auto_backup -> {
                showAutoBackupDialog()
                return true
            }

            R.id.action_restore -> {
                openRestoreDocument.launch(arrayOf("*/*"))
                return true
            }

            R.id.action_cloud_backup -> {
                startActivity(CloudBackupActivity.intent(this))
                return true
            }

            R.id.action_sync -> {
                startActivity(SyncActivity.intent(this))
                return true
            }

            R.id.action_instruments -> {
                startActivity(InstrumentsActivity.intent(this))
                return true
            }

            R.id.action_balances -> {
                startActivity(BalancesActivity.intent(this))
                return true
            }

            R.id.action_release_notes -> {
                startActivity(ChangelogActivity.intent(this))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /** Switch for the daily automatic backup, plus when it last ran and where the files are. */
    private fun showAutoBackupDialog() {
        val status = autoBackupViewModel.status()
        val view = layoutInflater.inflate(R.layout.dialog_auto_backup, null)
        view.findViewById<SwitchCompat>(R.id.switch_auto_backup).apply {
            isChecked = status.enabled
            setOnCheckedChangeListener { _, checked -> autoBackupViewModel.setEnabled(checked) }
        }
        view.findViewById<TextView>(R.id.text_auto_backup_last).text =
            status.lastBackupAt?.let {
                getString(R.string.auto_backup_last, DateFormat.getDateTimeInstance().format(Date(it)))
            } ?: getString(R.string.auto_backup_last_never)
        view.findViewById<TextView>(R.id.text_auto_backup_folder).text =
            status.folderPath?.let { getString(R.string.auto_backup_folder, it) }
                ?: getString(R.string.auto_backup_folder_unknown)
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_backup_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        operationInProgress = inProgress
        invalidateOptionsMenu()
    }

    private fun confirmRestore(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_restore_title)
            .setMessage(R.string.dialog_restore_message)
            .setPositiveButton(R.string.action_restore) { _, _ ->
                performRestore(
                    uri
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performBackup(uri: Uri) {
        setOperationInProgress(true)
        Thread {
            try {
                val json = backupRepository.export()
                contentResolver.openOutputStream(uri)
                    ?.use { it.write(json.toByteArray()) }
                runOnUiThread {
                    Toast.makeText(
                        this,
                        R.string.backup_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.backup_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                runOnUiThread { setOperationInProgress(false) }
            }
        }.start()
    }

    internal fun showRestoreOlderVersionWarning(backupVersion: Int, currentVersion: Int) {
        RestoreResultPresenter.showOlderVersionWarning(this, backupVersion, currentVersion)
    }

    private fun performRestore(uri: Uri) {
        setOperationInProgress(true)
        Thread {
            try {
                val json = contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?.toString(Charsets.UTF_8)
                    ?: throw IllegalStateException("Could not read backup file")
                val result = backupRepository.restore(json)
                runOnUiThread { RestoreResultPresenter.show(this, result) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.restore_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                runOnUiThread { setOperationInProgress(false) }
            }
        }.start()
    }

    companion object {
        private val backupFilenameDateFormat =
            SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
    }
}
