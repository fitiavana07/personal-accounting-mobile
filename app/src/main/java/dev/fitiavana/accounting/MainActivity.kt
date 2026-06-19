package dev.fitiavana.accounting

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import dev.fitiavana.accounting.ui.accounts.AccountsFragment
import dev.fitiavana.accounting.ui.roadmap.RoadmapFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AccountsFragment())
                .commit()
        }

        findViewById<BottomNavigationView>(R.id.bottom_navigation)
            .setOnItemSelectedListener { item ->
                val fragment = when (item.itemId) {
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