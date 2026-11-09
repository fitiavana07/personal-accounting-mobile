package dev.fitiavana.accounting.ui.bankaccounts

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.bankaccounts.BankAccount
import dev.fitiavana.accounting.features.bankaccounts.BankAccountRepository
import dev.fitiavana.accounting.features.bankaccounts.BankAccountTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BankAccountDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val repository = mock<BankAccountRepository>()
    private val account = MutableLiveData<BankAccount?>(null)
    private val transactions = MutableLiveData<List<BankAccountTransaction>>(emptyList())

    @Before
    fun setUp() {
        whenever(repository.getAccountLive("a1")).thenReturn(account)
        whenever(repository.getTransactions("a1")).thenReturn(transactions)
    }

    private fun viewModel() = BankAccountDetailViewModel(repository, "a1")

    @Test
    fun `the account and its transactions come from the repository`() {
        val tx = BankAccountTransaction("t", "a1", -5, "n", 1, 2)
        account.value = BankAccount("a1", "BOA", -5, 2)
        transactions.value = listOf(tx)

        val vm = viewModel()

        assertEquals("BOA", vm.account.value?.name)
        assertEquals(listOf(tx), vm.transactions.value)
    }

    @Test
    fun `renaming saves the trimmed name`() {
        assertTrue(viewModel().renameSync(" BNI "))

        verify(repository).rename("a1", "BNI")
    }

    @Test
    fun `a blank name renames nothing`() {
        assertFalse(viewModel().renameSync(""))

        verify(repository, never()).rename(any(), any())
    }
}
