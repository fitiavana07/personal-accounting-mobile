package dev.fitiavana.accounting.ui.transactions

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.transactions.TransactionRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TransactionsViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val transactionRepository: TransactionRepository = mock()
    private val accountRepository: AccountRepository = mock()
    private val balanceRepository: BalanceRepository = mock()
    private lateinit var viewModel: TransactionsViewModel

    @Before
    fun setUp() {
        whenever(accountRepository.getAll()).thenReturn(MutableLiveData(emptyList()))
        whenever(transactionRepository.getFilteredWithEntries(
            org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.anyOrNull()
        )).thenReturn(MutableLiveData(emptyList()))
        viewModel = TransactionsViewModel(
            transactionRepository, accountRepository, balanceRepository, runInBackground = { it() }
        )
    }

    @Test
    fun `clearing all transactions then recalculates every balance`() {
        viewModel.clearAllTransactions()

        inOrder(transactionRepository, balanceRepository) {
            verify(transactionRepository).clearAll()
            verify(balanceRepository).recalculateAll()
        }
    }
}
