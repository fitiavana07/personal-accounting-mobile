package dev.fitiavana.accounting.ui.accounts

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountTypes
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
        liquidityLevel: String? = null,
        aprPercent: Double? = null,
        incomeOnIntermediary: Boolean = false
    ) {
        val trimmed = name.trim()
        val supportsInstrument = AccountTypes.supportsInstrument(type)
        val savedInstrumentCode = instrumentCode.takeIf { supportsInstrument }
        val savedIntermediaryCode = intermediaryInstrumentCode.takeIf { supportsInstrument }
        // Only asset accounts earn interest, and a zero or negative rate means "none".
        val savedApr = aprPercent.takeIf { type == AccountTypes.ASSET && it != null && it > 0.0 }
        // The flag only means something for an asset account that has an intermediary instrument.
        val savedIncomeOnIntermediary =
            incomeOnIntermediary && type == AccountTypes.ASSET && savedIntermediaryCode != null
        val account = Account(
            id = id ?: UUID.randomUUID().toString(),
            name = trimmed,
            type = type,
            instrumentCode = savedInstrumentCode,
            intermediaryInstrumentCode = savedIntermediaryCode,
            liquidityLevel = liquidityLevel,
            aprPercent = savedApr,
            incomeOnIntermediary = savedIncomeOnIntermediary
        )
        if (id == null) repository.insert(account) else repository.update(account)
    }

    fun deleteAccount(account: Account) {
        repository.delete(account)
    }
}