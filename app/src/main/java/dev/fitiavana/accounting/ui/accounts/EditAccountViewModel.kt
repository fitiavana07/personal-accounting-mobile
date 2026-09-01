package dev.fitiavana.accounting.ui.accounts

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import java.util.UUID

class EditAccountViewModel(
    private val repository: AccountRepository,
    private val instrumentRepository: InstrumentRepository,
    private val balanceRepository: BalanceRepository
) : ViewModel() {

    val instruments: LiveData<List<Instrument>> = instrumentRepository.getAll()

    fun getAccount(id: String): Account? = repository.getById(id)

    fun hasTransactions(id: String): Boolean = balanceRepository.hasTransactions(id)

    fun saveAccount(
        id: String?,
        name: String,
        type: String,
        instrumentCode: String?,
        intermediaryInstrumentCode: String?,
        liquidityLevel: String? = null
    ) {
        val trimmed = name.trim()
        if (id == null) {
            repository.insert(
                Account(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    type = type,
                    instrumentCode = instrumentCode,
                    intermediaryInstrumentCode = intermediaryInstrumentCode,
                    liquidityLevel = liquidityLevel
                )
            )
        } else {
            repository.update(
                Account(
                    id = id,
                    name = trimmed,
                    type = type,
                    instrumentCode = instrumentCode,
                    intermediaryInstrumentCode = intermediaryInstrumentCode,
                    liquidityLevel = liquidityLevel
                )
            )
        }
    }

    fun deleteAccount(account: Account) {
        repository.delete(account)
    }
}