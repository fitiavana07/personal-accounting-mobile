package dev.fitiavana.accounting.ui.instruments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

class InstrumentsActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, InstrumentsActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instruments)

        UiUtils.setupActionBar(this)
        title = getString(R.string.instruments)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, InstrumentsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}