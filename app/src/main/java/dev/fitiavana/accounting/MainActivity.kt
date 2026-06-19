package dev.fitiavana.accounting

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.fitiavana.accounting.ui.accounts.AccountsFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AccountsFragment())
                .commit()
        }
    }
}