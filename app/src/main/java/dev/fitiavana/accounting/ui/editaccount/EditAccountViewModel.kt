package dev.fitiavana.accounting.ui.editaccount

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.InstrumentRepository
import java.util.UUID

class EditAccountViewModel(
    private val repository: AccountRepository,
    private val instrumentRepository: InstrumentRepository
) : ViewModel() {

    val instruments: LiveData<List<Instrument>> = instrumentRepository.getAll()

    fun getAccount(id: String): Account? = repository.getById(id)

    fun saveAccount(id: String?, name: String, type: String, instrumentCode: String?) {
        val trimmed = name.trim()
        if (id == null) {
            repository.insert(Account(id = UUID.randomUUID().toString(), name = trimmed, type = type, instrumentCode = instrumentCode))
        } else {
            repository.update(Account(id = id, name = trimmed, type = type, instrumentCode = instrumentCode))
        }
    }

    fun deleteAccount(account: Account) {
        repository.delete(account)
    }
}