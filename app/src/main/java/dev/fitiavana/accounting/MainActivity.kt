package dev.fitiavana.accounting

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.fitiavana.accounting.ui.accounts.AccountsFragment
import dev.fitiavana.accounting.ui.balances.BalancesFragment
import dev.fitiavana.accounting.ui.roadmap.RoadmapFragment
import dev.fitiavana.accounting.ui.transactions.TransactionsFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
                val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.setPadding(0, statusBar.top, 0, 0)
                insets
            }
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
                val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBar.bottom)
                insets
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BalancesFragment())
                .commit()
            bottomNav.selectedItemId = R.id.nav_balances
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_balances -> BalancesFragment()
                R.id.nav_transactions -> TransactionsFragment()
                R.id.nav_accounts -> AccountsFragment()
                R.id.nav_roadmap -> RoadmapFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }
    }
}
