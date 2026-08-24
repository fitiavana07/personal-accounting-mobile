package dev.fitiavana.accounting.ui.instruments

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

class InstrumentsViewModel(private val repository: InstrumentRepository) : ViewModel() {
    val instruments: LiveData<List<Instrument>> = repository.getAll()
}
