package dev.fitiavana.accounting.ui.balances

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

class BalancesActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, BalancesActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_balances)

        UiUtils.setupActionBar(this)
        title = getString(R.string.nav_balances)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BalancesFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
