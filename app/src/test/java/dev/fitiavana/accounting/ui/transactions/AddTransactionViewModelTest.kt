package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.features.transactions.TransactionRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddTransactionViewModelTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var balanceRepository: BalanceRepository
    private lateinit var instrumentRepository: InstrumentRepository
    private lateinit var viewModel: AddTransactionViewModel

    private val cashAccount = Account(id = "cash", name = "Cash", type = "asset")
    private val revenueAccount =
        Account(id = "rev", name = "Revenue", type = "revenue")
    private val usd = Instrument(code = "USD", note = "Dollar", type = "fiat")

    @Before
    fun setUp() {
        transactionRepository = mock()
        accountRepository = mock()
        balanceRepository = mock()
        instrumentRepository = mock()
        viewModel = AddTransactionViewModel(
            transactionRepository,
            accountRepository,
            balanceRepository,
            instrumentRepository
        )
    }

    @Test
    fun `loadAccountsAndInstruments returns accounts and instruments keyed by code`() {
        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(cashAccount, revenueAccount)
        )
        whenever(instrumentRepository.getAllSync()).thenReturn(listOf(usd))

        val result = viewModel.loadAccountsAndInstruments()

        assertEquals(listOf(cashAccount, revenueAccount), result.accounts)
        assertEquals(usd, result.instrumentsByCode["USD"])
    }

    @Test
    fun `getBalance delegates to the balance repository`() {
        val balance = AccountBalance(
            accountId = "cash",
            balance = 500L,
            updatedAt = 0L,
            createdAt = 0L
        )
        whenever(balanceRepository.getByAccountId("cash")).thenReturn(balance)

        assertEquals(balance, viewModel.getBalance("cash"))
    }

    @Test
    fun `saveTransaction inserts the transaction, entries and recalculates each account balance`() {
        val transaction = Transaction(
            id = "t1",
            createdAt = 0L,
            transactionDatetime = 0L,
            note = ""
        )
        val entries = listOf(
            TransactionEntry(
                id = "e1",
                transactionId = "t1",
                accountId = "cash",
                debitAmount = 100L,
                creditAmount = null
            ),
            TransactionEntry(
                id = "e2",
                transactionId = "t1",
                accountId = "rev",
                debitAmount = null,
                creditAmount = 100L
            )
        )
        val accountTypesById = mapOf("cash" to "asset", "rev" to "revenue")

        viewModel.saveTransaction(transaction, entries, accountTypesById)

        verify(transactionRepository).insert(transaction)
        verify(transactionRepository).insertEntry(entries[0])
        verify(transactionRepository).insertEntry(entries[1])
        verify(balanceRepository).recalculateForAccount("cash", "asset")
        verify(balanceRepository).recalculateForAccount("rev", "revenue")
    }
}
