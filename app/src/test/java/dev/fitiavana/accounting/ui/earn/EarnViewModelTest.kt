package dev.fitiavana.accounting.ui.earn

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EarnViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val accounts = MutableLiveData<List<Account>>()
    private val balances = MutableLiveData<List<AccountBalance>>()
    private val instruments = MutableLiveData<List<Instrument>>()
    private lateinit var viewModel: EarnViewModel

    private fun balance(id: String, base: Long) =
        AccountBalance(accountId = id, balance = base, updatedAt = 0L, createdAt = 0L)

    @Before
    fun setUp() {
        val accountRepository: AccountRepository = mock()
        val balanceRepository: BalanceRepository = mock()
        val instrumentRepository: InstrumentRepository = mock()
        whenever(accountRepository.getAll()).thenReturn(accounts)
        whenever(balanceRepository.getAll()).thenReturn(balances)
        whenever(instrumentRepository.getAll()).thenReturn(instruments)
        viewModel = EarnViewModel(accountRepository, balanceRepository, instrumentRepository)
        viewModel.state.observeForever { }
    }

    @Test
    fun `lists only the Earn accounts once all data has arrived`() {
        accounts.value = listOf(
            Account(id = "earn", name = "Earn", type = "asset", aprPercent = 10.0),
            Account(id = "cash", name = "Cash", type = "asset")
        )
        balances.value = listOf(balance("earn", 1_000_000L), balance("cash", 5_000L))
        instruments.value = emptyList()

        val state = viewModel.state.value
        assertEquals(listOf("earn"), state?.items?.map { it.account.id })
        assertEquals(100_000L, state?.totals?.yearly)
    }

    @Test
    fun `an empty database gives an empty state`() {
        accounts.value = emptyList()
        balances.value = emptyList()
        instruments.value = emptyList()

        assertTrue(viewModel.state.value?.items?.isEmpty() == true)
    }

    @Test
    fun `the state follows later balance changes`() {
        accounts.value = listOf(Account(id = "earn", name = "Earn", type = "asset", aprPercent = 10.0))
        balances.value = listOf(balance("earn", 1_000_000L))
        instruments.value = emptyList()

        balances.value = listOf(balance("earn", 2_000_000L))

        assertEquals(200_000L, viewModel.state.value?.totals?.yearly)
    }

    @Test
    fun `the state follows an APR being added to an account`() {
        accounts.value = listOf(Account(id = "a", name = "A", type = "asset"))
        balances.value = listOf(balance("a", 1_000_000L))
        instruments.value = emptyList()
        assertTrue(viewModel.state.value?.items?.isEmpty() == true)

        accounts.value = listOf(Account(id = "a", name = "A", type = "asset", aprPercent = 5.0))

        assertEquals(listOf("a"), viewModel.state.value?.items?.map { it.account.id })
    }
}
