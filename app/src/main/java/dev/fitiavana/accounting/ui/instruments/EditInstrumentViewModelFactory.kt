package dev.fitiavana.accounting.ui.instruments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

class EditInstrumentViewModelFactory(private val repository: InstrumentRepository) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EditInstrumentViewModel(repository) as T
    }
}
