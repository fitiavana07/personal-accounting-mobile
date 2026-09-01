package dev.fitiavana.accounting.ui.accounts

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AccountsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private fun account(id: String, name: String, type: String = "asset") =
        Account(id = id, name = name, type = type)

    private fun balance(accountId: String, balance: Long) = AccountBalance(
        accountId = accountId,
        balance = balance,
        updatedAt = 1000L,
        createdAt = 1000L
    )

    private lateinit var viewModel: AccountsViewModel

    @Before
    fun setUp() {
        val accountRepository: AccountRepository = mock()
        val balanceRepository: BalanceRepository = mock()
        val instrumentRepository: InstrumentRepository = mock()

        whenever(accountRepository.getAll()).thenReturn(
            MutableLiveData(
                listOf(
                    account("acc1", "Cash"),
                    account("acc2", "Zeroed Out"),
                    account("acc3", "New Account")
                )
            )
        )
        whenever(balanceRepository.getAll()).thenReturn(
            MutableLiveData(
                listOf(
                    balance("acc1", 1000L),
                    balance("acc2", 0L)
                    // acc3 has no balance row (zero balance, null updatedAt)
                )
            )
        )
        whenever(instrumentRepository.getAll()).thenReturn(MutableLiveData(emptyList()))

        viewModel = AccountsViewModel(accountRepository, balanceRepository, instrumentRepository)
        // MediatorLiveData only computes while it has an active observer.
        viewModel.accounts.observeForever { }
    }

    @Test
    fun `hideZeroBalance defaults to true and hides zero-balance accounts`() {
        assertEquals(true, viewModel.hideZeroBalance.value)
        assertEquals(listOf("Cash"), viewModel.accounts.value?.map { it.account.name })
    }

    @Test
    fun `disabling hideZeroBalance shows all accounts including zero-balance ones`() {
        viewModel.setHideZeroBalance(false)

        assertEquals(
            listOf("Cash", "Zeroed Out", "New Account"),
            viewModel.accounts.value?.map { it.account.name }
        )
    }

    @Test
    fun `type filter and hideZeroBalance filter combine`() {
        viewModel.setHideZeroBalance(false)
        viewModel.setTypeFilter("asset")

        assertEquals(3, viewModel.accounts.value?.size)

        viewModel.setTypeFilter("liability")

        assertEquals(true, viewModel.accounts.value?.isEmpty())
    }
}
