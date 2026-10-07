package dev.fitiavana.accounting.ui.transactions

import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.templates.TemplateRepository
import dev.fitiavana.accounting.features.templates.TemplateSlot
import dev.fitiavana.accounting.features.templates.TemplateWithEntries
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.features.transactions.TransactionRepository

data class AccountsAndInstruments(
    val accounts: List<Account>,
    val instrumentsByCode: Map<String, Instrument>
)

class AddTransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val balanceRepository: BalanceRepository,
    private val instrumentRepository: InstrumentRepository,
    private val templateRepository: TemplateRepository
) : ViewModel() {

    /** Synchronous — callers must invoke this off the main thread. */
    fun loadTemplates(): List<TemplateWithEntries> = templateRepository.getAll()

    /** Synchronous — callers must invoke this off the main thread. */
    fun loadTemplate(id: String): TemplateWithEntries? = templateRepository.get(id)

    /** Synchronous — callers must invoke this off the main thread. */
    fun saveTemplate(name: String, mode: String, slots: List<TemplateSlot>) =
        templateRepository.save(name, mode, slots)

    /** Synchronous — callers must invoke this off the main thread. */
    fun deleteTemplate(id: String) = templateRepository.delete(id)


    /** Synchronous — callers must invoke this off the main thread. */
    fun loadAccountsAndInstruments(): AccountsAndInstruments =
        AccountsAndInstruments(
            accountRepository.getAllSync(),
            instrumentRepository.getAllSync().associateBy { it.code }
        )

    /** Synchronous — callers must invoke this off the main thread. */
    fun getBalance(accountId: String): AccountBalance? =
        balanceRepository.getByAccountId(accountId)

    /** Synchronous — callers must invoke this off the main thread. */
    fun saveTransaction(
        transaction: Transaction,
        entries: List<TransactionEntry>,
        accountTypesById: Map<String, String>
    ) {
        transactionRepository.insert(transaction)
        entries.forEach { transactionRepository.insertEntry(it) }
        entries.forEach { entry ->
            val type = accountTypesById.getValue(entry.accountId)
            balanceRepository.recalculateForAccount(entry.accountId, type)
        }
    }
}
