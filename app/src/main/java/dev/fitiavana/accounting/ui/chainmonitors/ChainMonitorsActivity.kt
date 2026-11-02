package dev.fitiavana.accounting.ui.chainmonitors

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

/** The chain monitors: notifications when a contract's token balance on Base goes above an amount. */
class ChainMonitorsActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, ChainMonitorsActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chain_monitors)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_chain_monitors)

        val controller = ChainMonitorsController(this)

        val adapter = ChainMonitorsAdapter(
            onClick = controller::edit,
            onDelete = controller::confirmDelete
        )
        val recycler = findViewById<RecyclerView>(R.id.recycler_chain_monitors).apply {
            layoutManager = LinearLayoutManager(this@ChainMonitorsActivity)
            this.adapter = adapter
        }
        val emptyView = findViewById<View>(R.id.text_chain_monitors_empty)

        controller.viewModel.items.observe(this) { items ->
            adapter.submit(items)
            val hasItems = items.isNotEmpty()
            recycler.visibility = if (hasItems) View.VISIBLE else View.GONE
            emptyView.visibility = if (hasItems) View.GONE else View.VISIBLE
        }

        findViewById<View>(R.id.button_add_chain_monitor).setOnClickListener { controller.add() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
