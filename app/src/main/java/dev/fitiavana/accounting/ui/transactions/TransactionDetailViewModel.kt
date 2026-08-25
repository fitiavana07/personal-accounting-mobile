package dev.fitiavana.accounting.ui.transactions

import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.transactions.TransactionRepository
import dev.fitiavana.accounting.features.transactions.TransactionWithEntries

data class TransactionDetailData(
    val transactionWithEntries: TransactionWithEntries,
    val accountsById: Map<String, Account>,
    val instrumentsByCode: Map<String, Instrument>
)

class TransactionDetailViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository
) : ViewModel() {

    /** Synchronous — callers must invoke this off the main thread. */
    fun loadDetail(transactionId: String): TransactionDetailData? {
        val twe = transactionRepository.getWithEntries(transactionId) ?: return null
        val accounts =
            accountRepository.getAll().value ?: accountRepository.getAllSync()
        val instruments = instrumentRepository.getAllSync().associateBy { it.code }
        return TransactionDetailData(
            twe,
            accounts.associateBy { it.id },
            instruments
        )
    }
}
