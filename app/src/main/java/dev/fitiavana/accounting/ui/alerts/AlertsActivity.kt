package dev.fitiavana.accounting.ui.alerts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

/** The gain/loss alerts: notifications when an account's gain/loss % crosses a threshold. */
class AlertsActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, AlertsActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_alerts)

        val controller = AlertsController(this)

        val adapter = AlertsAdapter(
            onClick = controller::edit,
            onDelete = controller::confirmDelete
        )
        val recycler = findViewById<RecyclerView>(R.id.recycler_alerts).apply {
            layoutManager = LinearLayoutManager(this@AlertsActivity)
            this.adapter = adapter
        }
        val emptyView = findViewById<View>(R.id.text_alerts_empty)

        controller.viewModel.items.observe(this) { items ->
            adapter.submit(items)
            val hasItems = items.isNotEmpty()
            recycler.visibility = if (hasItems) View.VISIBLE else View.GONE
            emptyView.visibility = if (hasItems) View.GONE else View.VISIBLE
        }

        findViewById<View>(R.id.button_add_alert).setOnClickListener { controller.add() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
