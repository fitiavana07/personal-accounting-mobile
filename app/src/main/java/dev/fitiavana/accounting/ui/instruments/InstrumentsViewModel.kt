package dev.fitiavana.accounting.ui.instruments

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.repository.InstrumentRepository

class InstrumentsViewModel(private val repository: InstrumentRepository) : ViewModel() {
    val instruments: LiveData<List<Instrument>> = repository.getAll()
}
