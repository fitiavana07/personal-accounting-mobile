package dev.fitiavana.accounting.ui.cexprices

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.cexprices.CexPrice
import dev.fitiavana.accounting.ui.common.UiUtils
import java.util.Locale

class CexPricesActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, CexPricesActivity::class.java)
    }

    private lateinit var viewModel: CexPricesViewModel
    private lateinit var spinnerBase: Spinner
    private lateinit var spinnerQuote: Spinner
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val pricesAdapter = CexPricesAdapter()
    private var codes: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cex_prices)
        UiUtils.setupActionBar(this)
        title = getString(R.string.title_cex_prices)

        val container = AppContainer.getInstance(this)
        viewModel = ViewModelProvider(
            this,
            CexPricesViewModelFactory(container.instrumentRepository, container.cexPriceRepository)
        ).get(CexPricesViewModel::class.java)

        spinnerBase = findViewById(R.id.spinner_base)
        spinnerQuote = findViewById(R.id.spinner_quote)
        swipeRefresh = findViewById(R.id.swipe_refresh_cex_prices)
        swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        findViewById<RecyclerView>(R.id.recycler_cex_prices).apply {
            layoutManager = LinearLayoutManager(this@CexPricesActivity)
            adapter = pricesAdapter
        }

        spinnerBase.onItemSelectedListener = selectionListener(viewModel::selectBase)
        spinnerQuote.onItemSelectedListener = selectionListener(viewModel::selectQuote)
        observeViewModel()
    }

    private fun selectionListener(onSelected: (String) -> Unit) =
        object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                codes.getOrNull(position)?.let(onSelected)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

    private fun observeViewModel() {
        viewModel.cryptoInstruments.observe(this) { instruments ->
            showCodes(instruments.map { it.code })
        }
        viewModel.prices.observe(this) { prices ->
            showComparison(prices)
            updateHint()
        }
        viewModel.loading.observe(this) { loading ->
            swipeRefresh.isRefreshing = loading
            updateHint()
        }
    }

    /** Fills both spinners, preferring the current or last saved pair when still available. */
    private fun showCodes(newCodes: List<String>) {
        codes = newCodes
        setSpinnerItems(spinnerBase, newCodes, newCodes.indexOf(viewModel.preferredBase).takeIf { it >= 0 } ?: 0)
        val defaultQuote = if (newCodes.size > 1) 1 else 0
        setSpinnerItems(spinnerQuote, newCodes, newCodes.indexOf(viewModel.preferredQuote).takeIf { it >= 0 } ?: defaultQuote)
    }

    private fun setSpinnerItems(spinner: Spinner, items: List<String>, selected: Int) {
        spinner.adapter = ArrayAdapter(this, R.layout.item_spinner_crypto, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(selected)
    }

    private fun showComparison(prices: List<CexPrice>?) {
        val card = findViewById<View>(R.id.card_cex_prices)
        if (prices == null) {
            card.visibility = View.GONE
            pricesAdapter.submit(emptyList())
            return
        }
        val comparison = CexPriceComparisonBuilder.build(prices)
        pricesAdapter.submit(comparison.rows)
        findViewById<TextView>(R.id.text_cex_pair).text =
            getString(R.string.cex_pair_format, viewModel.preferredBase, viewModel.preferredQuote)
        findViewById<TextView>(R.id.text_cex_spread).apply {
            val spread = comparison.spreadPercent
            visibility = if (spread == null) View.GONE else View.VISIBLE
            text = spread?.let { getString(R.string.cex_spread_format, String.format(Locale.US, "%.2f%%", it)) }
        }
        card.visibility = View.VISIBLE
    }

    private fun updateHint() {
        val showHint = viewModel.prices.value == null && viewModel.loading.value != true
        findViewById<View>(R.id.text_cex_hint).visibility = if (showHint) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
