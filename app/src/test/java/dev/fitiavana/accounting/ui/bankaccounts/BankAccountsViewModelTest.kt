package dev.fitiavana.accounting.ui.bankaccounts

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.bankaccounts.BankAccount
import dev.fitiavana.accounting.features.bankaccounts.BankAccountRepository
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

class BankAccountsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val repository = mock<BankAccountRepository>()
    private val accounts = MutableLiveData<List<BankAccount>>(emptyList())

    @Before
    fun setUp() {
        whenever(repository.getAccounts()).thenReturn(accounts)
    }

    private fun viewModel() = BankAccountsViewModel(repository)

    @Test
    fun `the accounts come from the repository`() {
        val list = listOf(BankAccount("a", "BOA", 10, 5))
        accounts.value = list

        assertEquals(list, viewModel().accounts.value)
    }

    @Test
    fun `creating an account saves it with the trimmed name`() {
        assertTrue(viewModel().createSync("  BOA "))

        verify(repository).createAccount("BOA")
    }

    @Test
    fun `a blank name creates nothing`() {
        assertFalse(viewModel().createSync("   "))

        verify(repository, never()).createAccount(any())
    }
}
