package dev.fitiavana.accounting.ui.editaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

class EditAccountViewModelFactory(
    private val repository: AccountRepository,
    private val instrumentRepository: InstrumentRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return EditAccountViewModel(repository, instrumentRepository) as T
    }
}