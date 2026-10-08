package dev.fitiavana.accounting.ui.earn

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

/** Projected daily, monthly and yearly interest of every asset account that has an APR. */
class EarnActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, EarnActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_earn)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_earn)

        val container = AppContainer.getInstance(this)
        val viewModel = ViewModelProvider(
            this,
            EarnViewModelFactory(
                container.accountRepository,
                container.balanceRepository,
                container.instrumentRepository
            )
        ).get(EarnViewModel::class.java)

        val adapter = EarnAdapter()
        val recycler = findViewById<RecyclerView>(R.id.recycler_earn).apply {
            layoutManager = LinearLayoutManager(this@EarnActivity)
            this.adapter = adapter
        }
        val emptyView = findViewById<View>(R.id.text_earn_empty)

        viewModel.state.observe(this) { state ->
            adapter.submit(state)
            val hasItems = state.items.isNotEmpty()
            recycler.visibility = if (hasItems) View.VISIBLE else View.GONE
            emptyView.visibility = if (hasItems) View.GONE else View.VISIBLE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
