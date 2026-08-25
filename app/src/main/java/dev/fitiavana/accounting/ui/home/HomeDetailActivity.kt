package dev.fitiavana.accounting.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils
import dev.fitiavana.accounting.ui.common.TransactionDisplay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeDetailActivity : AppCompatActivity() {

    private val dateFormat =
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_detail)

        UiUtils.setupActionBar(this)
        title = getString(R.string.title_home_detail)

        val accountId =
            intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: run { finish(); return }

        val container = AppContainer.getInstance(this)
        val viewModel = ViewModelProvider(
            this,
            HomeDetailViewModelFactory(
                container.balanceRepository,
                container.accountRepository,
                container.instrumentRepository,
                container.exchangeRateRepository
            )
        )
            .get(HomeDetailViewModel::class.java)

        Thread {
            val item = viewModel.findItem(accountId)
            runOnUiThread {
                if (item != null) bindData(item) else finish()
            }
        }.start()
    }

    private fun bindData(item: HomeItem) {
        title = item.accountName

        bindGainLoss(item)

        setText(R.id.text_detail_balance, item.instrumentBalanceFormatted)

        bindOptionalRow(
            R.id.row_market_value,
            R.id.text_detail_market_value,
            item.currentValue?.let {
                TransactionDisplay.formatInstrumentAmount(
                    Math.round(
                        it * Math.pow(
                            10.0,
                            item.intermediaryInstrument.decimalPlaces.toDouble()
                        )
                    ),
                    item.intermediaryInstrument
                )
            })

        bindOptionalRow(
            R.id.row_market_value_ar,
            R.id.text_detail_market_value_ar,
            item.currentValueAr?.let {
                "Ar ${TransactionDisplay.formatAmount(Math.round(it))}"
            })

        bindOptionalRow(
            R.id.row_market_price,
            R.id.text_detail_market_price,
            item.currentRate
        )

        setText(
            R.id.text_detail_book_value,
            TransactionDisplay.formatInstrumentAmount(
                Math.round(
                    item.bookValue * Math.pow(
                        10.0,
                        item.intermediaryInstrument.decimalPlaces.toDouble()
                    )
                ),
                item.intermediaryInstrument
            )
        )

        bindOptionalRow(
            R.id.row_book_price,
            R.id.text_detail_book_price,
            item.bookRate
        )

        setText(
            R.id.text_detail_price_updated_at,
            if (item.rateFetchedAt != null) {
                dateFormat.format(Date(item.rateFetchedAt))
            } else {
                getString(R.string.home_rate_never_updated)
            }
        )
    }

    private fun bindGainLoss(item: HomeItem) {
        bindSignedText(
            R.id.text_detail_gain_loss_percent,
            item.gainLossPercent,
            item.gainLossPercent?.let {
                GainLossFormatter.formatSignedPercent(
                    it
                )
            }
        )
        bindSignedText(
            R.id.text_detail_gain_loss_ar,
            item.gainLossAr,
            item.gainLossAr?.let { GainLossFormatter.formatSignedAmountAr(it) }
        )
        bindSignedText(
            R.id.text_detail_gain_loss_amount,
            item.gainLoss,
            item.gainLoss?.let {
                GainLossFormatter.formatSignedAmount(
                    it,
                    item.intermediaryInstrument
                )
            }
        )
    }

    private fun bindSignedText(viewId: Int, value: Double?, text: String?) {
        val view = findViewById<TextView>(viewId)
        if (value != null && text != null) {
            view.text = text
            view.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (value >= 0) R.color.gain else R.color.loss
                )
            )
            view.visibility = View.VISIBLE
        } else {
            view.visibility = View.GONE
        }
    }

    private fun bindOptionalRow(rowId: Int, valueId: Int, value: String?) {
        val row = findViewById<View>(rowId)
        if (value != null) {
            findViewById<TextView>(valueId).text = value
            row.visibility = View.VISIBLE
        } else {
            row.visibility = View.GONE
        }
    }

    private fun setText(viewId: Int, text: String) {
        findViewById<TextView>(viewId).text = text
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
