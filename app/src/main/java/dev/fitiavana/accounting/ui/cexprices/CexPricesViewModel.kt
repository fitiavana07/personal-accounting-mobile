package dev.fitiavana.accounting.ui.cexprices

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.cexprices.CexPrice
import dev.fitiavana.accounting.features.cexprices.CexPriceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class CexPricesViewModel(
    instrumentRepository: InstrumentRepository,
    private val cexPriceRepository: CexPriceRepository,
    private val executor: Executor = Executors.newSingleThreadExecutor()
) : ViewModel() {

    val cryptoInstruments: LiveData<List<Instrument>> = instrumentRepository.getCryptocurrencies()

    private val _prices = MutableLiveData<List<CexPrice>?>()
    val prices: LiveData<List<CexPrice>?> = _prices

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private var base: String? = null
    private var quote: String? = null

    /** Incremented on every (re)start so results of superseded requests can be dropped. */
    @Volatile
    private var requestId = 0

    fun selectBase(code: String) {
        if (code == base) return
        base = code
        refresh()
    }

    fun selectQuote(code: String) {
        if (code == quote) return
        quote = code
        refresh()
    }

    /** Re-fetches the prices of the selected pair; clears them if the pair is incomplete or degenerate. */
    fun refresh() {
        val id = ++requestId
        val base = base
        val quote = quote
        if (base == null || quote == null || base == quote) {
            _prices.value = null
            _loading.value = false
            return
        }
        _loading.value = true
        executor.execute {
            val result = cexPriceRepository.fetchAll(base, quote)
            if (id == requestId) {
                _prices.postValue(result)
                _loading.postValue(false)
            }
        }
    }
}
