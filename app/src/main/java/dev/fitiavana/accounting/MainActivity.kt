package dev.fitiavana.accounting

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.fitiavana.accounting.data.repository.BackupRepository
import dev.fitiavana.accounting.data.repository.RestoreResult
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.ui.UiUtils
import dev.fitiavana.accounting.ui.accounts.AccountsFragment
import dev.fitiavana.accounting.ui.balances.BalancesActivity
import dev.fitiavana.accounting.ui.home.HomeFragment
import dev.fitiavana.accounting.ui.instruments.InstrumentsActivity
import dev.fitiavana.accounting.ui.reports.ReportsFragment
import dev.fitiavana.accounting.ui.transactions.TransactionsFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var backupRepository: BackupRepository
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

        val db = AppDatabase.getInstance(this)
        backupRepository = BackupRepository(
            db,
            db.accountDao(),
            db.instrumentDao(),
            db.transactionDao(),
            db.accountBalanceDao(),
            db.exchangeRateCacheDao()
        )

        UiUtils.setupActionBar(this, displayHomeAsUp = false)
        supportActionBar?.title = getString(R.string.app_name_with_version, BuildConfig.VERSION_NAME)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
                val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBar.bottom)
                insets
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
            R.id.action_restore -> {
                openRestoreDocument.launch(arrayOf("*/*"))
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
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        operationInProgress = inProgress
        invalidateOptionsMenu()
    }

    private fun confirmRestore(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_restore_title)
            .setMessage(R.string.dialog_restore_message)
            .setPositiveButton(R.string.action_restore) { _, _ -> performRestore(uri) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performBackup(uri: Uri) {
        setOperationInProgress(true)
        Thread {
            try {
                val json = backupRepository.export()
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                runOnUiThread {
                    Toast.makeText(this, R.string.backup_success, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.backup_failed, e.message), Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { setOperationInProgress(false) }
            }
        }.start()
    }

    private fun performRestore(uri: Uri) {
        setOperationInProgress(true)
        Thread {
            try {
                val json = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?.toString(Charsets.UTF_8)
                    ?: throw IllegalStateException("Could not read backup file")
                when (val result = backupRepository.restore(json)) {
                    is RestoreResult.Success -> runOnUiThread {
                        Toast.makeText(this, R.string.restore_success, Toast.LENGTH_SHORT).show()
                    }
                    is RestoreResult.SchemaMismatch -> runOnUiThread {
                        Toast.makeText(
                            this,
                            getString(R.string.restore_schema_mismatch, result.backupVersion, result.currentVersion),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is RestoreResult.Error -> runOnUiThread {
                        Toast.makeText(this, getString(R.string.restore_failed, result.message), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.restore_failed, e.message), Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread { setOperationInProgress(false) }
            }
        }.start()
    }

    companion object {
        private val backupFilenameDateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
    }
}
