package dev.fitiavana.accounting.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.alerts.AlertsAdapter
import dev.fitiavana.accounting.ui.alerts.AlertsController
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import dev.fitiavana.accounting.ui.common.UiUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeDetailActivity : AppCompatActivity() {

    private lateinit var viewModel: HomeDetailViewModel
    private lateinit var accountId: String
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val dateFormat =
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_detail)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_home_detail)

        accountId =
            intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: run { finish(); return }

        val container = AppContainer.getInstance(this)
        viewModel = ViewModelProvider(
            this,
            HomeDetailViewModelFactory(
                container.balanceRepository,
                container.accountRepository,
                container.instrumentRepository,
                container.exchangeRateRepository,
                container.rateRefresher,
                container.alertCheckManager
            )
        )
            .get(HomeDetailViewModel::class.java)

        swipeRefresh = findViewById(R.id.swipe_refresh_detail)
        swipeRefresh.setOnRefreshListener { refreshRates() }

        bindAlerts()

        loadItem(finishIfMissing = true)
    }

    /**
     * Lists this account's alerts under the price card, with a button to add one for it. The cards are added to a
     * plain LinearLayout: a wrap_content RecyclerView inside the ScrollView was measured too short at large font
     * scales (125%), hiding every alert after the first.
     */
    private fun bindAlerts() {
        val controller = AlertsController(this)
        val container = findViewById<LinearLayout>(R.id.container_detail_alerts)
        controller.viewModel.items.observe(this) { items ->
            container.removeAllViews()
            items.filter { it.alert.accountId == accountId }.forEach { item ->
                val holder = AlertsAdapter.ViewHolder.inflate(container)
                holder.bind(item, controller::edit, controller::confirmDelete)
                container.addView(holder.itemView)
            }
        }
        findViewById<View>(R.id.button_detail_add_alert).setOnClickListener { controller.add(accountId) }
    }

    private fun loadItem(finishIfMissing: Boolean = false, onLoaded: () -> Unit = {}) {
        Thread {
            val item = viewModel.findItem(accountId)
            runOnUiThread {
                if (item != null) {
                    bindData(item)
                    onLoaded()
                } else if (finishIfMissing) {
                    finish()
                }
            }
        }.start()
    }

    private fun refreshRates() {
        swipeRefresh.isRefreshing = true
        Thread {
            val result = viewModel.refreshRates(accountId)
            runOnUiThread {
                loadItem { swipeRefresh.isRefreshing = false }
                if (result.failed.isNotEmpty() && result.succeeded.isEmpty()) {
                    Toast.makeText(
                        this,
                        R.string.home_refresh_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun bindData(item: HomeItem) {
        title = item.accountName

        findViewById<TextView>(R.id.text_detail_instrument).text =
            "${item.instrument.code} · ${item.instrument.type}"
                .uppercase(Locale.getDefault())
        bindGainLoss(item)

        val decimals = Math.pow(
            10.0, item.intermediaryInstrument.decimalPlaces.toDouble()
        )
        fun formatIntermediary(value: Double) =
            TransactionDisplay.formatInstrumentAmount(
                Math.round(value * decimals), item.intermediaryInstrument
            )

        val position = findViewById<ViewGroup>(R.id.card_detail_position)
        position.removeAllViews()
        addRow(position, R.string.home_detail_label_balance, item.instrumentBalanceFormatted)
        addRow(
            position,
            R.string.home_detail_label_cost_basis,
            UiUtils.formatAmountAr(this, item.bookValueAr)
        )
        addRow(
            position,
            R.string.home_detail_label_market_value_ar,
            item.currentValueAr?.let { UiUtils.formatAmountAr(this, Math.round(it)) }
        )
        addRow(position, R.string.home_detail_label_book_value, formatIntermediary(item.bookValue))
        addRow(
            position,
            R.string.home_detail_label_market_value,
            item.currentValue?.let { formatIntermediary(it) }
        )

        val price = findViewById<ViewGroup>(R.id.card_detail_price)
        price.removeAllViews()
        addRow(price, R.string.home_detail_label_market_price, item.currentRate)
        addRow(price, R.string.home_detail_label_book_price, item.bookRate)
        item.breakEvenPercent?.let {
            if (it > 0) {
                addRow(price, R.string.home_detail_label_break_even, GainLossFormatter.formatSignedPercent(it))
            } else {
                addRow(
                    price,
                    R.string.home_detail_label_cushion,
                    String.format(Locale.US, "%.1f%%", -it)
                )
            }
        }
        addRow(
            price,
            R.string.home_detail_label_price_updated_at,
            if (item.rateFetchedAt != null) {
                dateFormat.format(Date(item.rateFetchedAt))
            } else {
                getString(R.string.home_rate_never_updated)
            }
        )
    }

    private fun bindGainLoss(item: HomeItem) {
        HomeAdapter.bindSigned(
            this,
            findViewById(R.id.text_detail_gain_loss_percent),
            item.gainLossPercent,
            item.gainLossPercent?.let { GainLossFormatter.formatSignedPercent(it) },
            pill = true
        )
        HomeAdapter.bindSigned(
            this,
            findViewById(R.id.text_detail_gain_loss_ar),
            item.gainLossAr,
            item.gainLossAr?.let { GainLossFormatter.formatSignedAmountAr(it) },
            pill = false
        )
        val amount = findViewById<TextView>(R.id.text_detail_gain_loss_amount)
        amount.text = item.gainLoss?.let {
            GainLossFormatter.formatSignedAmount(it, item.intermediaryInstrument)
        }
        amount.visibility = if (item.gainLoss != null) View.VISIBLE else View.GONE
    }

    /** Appends a label/value row to [card] (with a divider); skipped when [value] is null. */
    private fun addRow(card: ViewGroup, labelRes: Int, value: String?) {
        if (value == null) return
        if (card.childCount > 0) {
            card.addView(
                layoutInflater.inflate(R.layout.item_detail_divider, card, false)
            )
        }
        val row = layoutInflater.inflate(R.layout.item_detail_row, card, false)
        row.findViewById<TextView>(R.id.text_row_label).setText(labelRes)
        row.findViewById<TextView>(R.id.text_row_value).text = value
        card.addView(row)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "extra_account_id"

        fun intent(context: Context, accountId: String): Intent =
            Intent(context, HomeDetailActivity::class.java).apply {
                putExtra(EXTRA_ACCOUNT_ID, accountId)
            }
    }
}
