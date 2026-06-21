package dev.fitiavana.accounting.ui.editinstrument

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.data.repository.InstrumentRepository

class EditInstrumentViewModelFactory(private val repository: InstrumentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return EditInstrumentViewModel(repository) as T
    }
}
