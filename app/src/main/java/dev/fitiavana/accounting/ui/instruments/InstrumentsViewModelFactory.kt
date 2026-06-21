package dev.fitiavana.accounting.ui.instruments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.data.repository.InstrumentRepository

class InstrumentsViewModelFactory(private val repository: InstrumentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return InstrumentsViewModel(repository) as T
    }
}
