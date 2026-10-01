package dev.fitiavana.accounting.ui.cexprices

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.fitiavana.accounting.AppContainer
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.ui.common.UiUtils

class CexPricesActivity : AppCompatActivity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, CexPricesActivity::class.java)
    }

    private lateinit var viewModel: CexPricesViewModel
    private lateinit var spinnerBase: Spinner
    private lateinit var spinnerQuote: Spinner
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
            pricesAdapter.submit(prices)
            updateHint()
        }
        viewModel.loading.observe(this) { loading ->
            findViewById<ProgressBar>(R.id.progress_cex_prices).visibility =
                if (loading) View.VISIBLE else View.INVISIBLE
            updateHint()
        }
    }

    /** Fills both spinners, keeping the previous selection when still available. */
    private fun showCodes(newCodes: List<String>) {
        val previousBase = spinnerBase.selectedItem as String?
        val previousQuote = spinnerQuote.selectedItem as String?
        codes = newCodes
        setSpinnerItems(spinnerBase, newCodes, newCodes.indexOf(previousBase).takeIf { it >= 0 } ?: 0)
        val defaultQuote = if (newCodes.size > 1) 1 else 0
        setSpinnerItems(spinnerQuote, newCodes, newCodes.indexOf(previousQuote).takeIf { it >= 0 } ?: defaultQuote)
    }

    private fun setSpinnerItems(spinner: Spinner, items: List<String>, selected: Int) {
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.setSelection(selected)
    }

    private fun updateHint() {
        val showHint = viewModel.prices.value == null && viewModel.loading.value != true
        findViewById<View>(R.id.text_cex_hint).visibility = if (showHint) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_cex_prices, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == R.id.action_refresh) {
            viewModel.refresh()
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
