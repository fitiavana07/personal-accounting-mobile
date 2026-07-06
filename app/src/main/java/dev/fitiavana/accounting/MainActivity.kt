package dev.fitiavana.accounting

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.fitiavana.accounting.ui.UiUtils
import dev.fitiavana.accounting.ui.accounts.AccountsFragment
import dev.fitiavana.accounting.ui.balances.BalancesFragment
import dev.fitiavana.accounting.ui.home.HomeFragment
import dev.fitiavana.accounting.ui.instruments.InstrumentsFragment
import dev.fitiavana.accounting.ui.transactions.TransactionsFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
                R.id.nav_balances -> BalancesFragment()
                R.id.nav_transactions -> TransactionsFragment()
                R.id.nav_accounts -> AccountsFragment()
                R.id.nav_instruments -> InstrumentsFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }
    }
}
