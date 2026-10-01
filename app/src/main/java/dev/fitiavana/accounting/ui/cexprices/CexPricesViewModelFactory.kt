package dev.fitiavana.accounting.ui.cexprices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.cexprices.CexPriceRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

class CexPricesViewModelFactory(
    private val instrumentRepository: InstrumentRepository,
    private val cexPriceRepository: CexPriceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CexPricesViewModel(instrumentRepository, cexPriceRepository) as T
    }
}
