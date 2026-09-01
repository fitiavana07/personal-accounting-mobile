package dev.fitiavana.accounting.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.accounts.LiquidityLevels
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.settings.AppSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HomeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var balanceRepository: BalanceRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var instrumentRepository: InstrumentRepository
    private lateinit var exchangeRateRepository: ExchangeRateRepository
    private lateinit var settingsRepository: AppSettingsRepository

    private lateinit var balances: MutableLiveData<List<AccountBalance>>
    private lateinit var accounts: MutableLiveData<List<Account>>
    private lateinit var instruments: MutableLiveData<List<Instrument>>
    private lateinit var rates: MutableLiveData<List<ExchangeRateCache>>
    private lateinit var settings: MutableLiveData<AppSettings?>

    private fun account(
        id: String,
        name: String,
        type: String = "asset",
        liquidityLevel: String? = LiquidityLevels.CASH_AND_EQUIVALENTS
    ) = Account(id = id, name = name, type = type, liquidityLevel = liquidityLevel)

    private fun balance(accountId: String, balance: Long) =
        AccountBalance(
            accountId = accountId,
            balance = balance,
            instrumentBalance = 0,
            intermediaryBalance = 0,
            updatedAt = 0L,
            createdAt = 0L
        )

    @Before
    fun setUp() {
        balances = MutableLiveData(emptyList())
        accounts = MutableLiveData(emptyList())
        instruments = MutableLiveData(emptyList())
        rates = MutableLiveData(emptyList())
        settings = MutableLiveData(null)

        balanceRepository = mock()
        accountRepository = mock()
        instrumentRepository = mock()
        exchangeRateRepository = mock()
        settingsRepository = mock()

        whenever(balanceRepository.getAll()).thenReturn(balances)
        whenever(accountRepository.getAll()).thenReturn(accounts)
        whenever(instrumentRepository.getAll()).thenReturn(instruments)
        whenever(exchangeRateRepository.getAllCached()).thenReturn(rates)
        whenever(settingsRepository.observe()).thenReturn(settings)
    }

    private fun viewModel(): HomeViewModel {
        val viewModel = HomeViewModel(
            balanceRepository,
            accountRepository,
            instrumentRepository,
            exchangeRateRepository,
            settingsRepository
        )
        // MediatorLiveData only forwards source updates while it has an active observer.
        viewModel.emergencyFund.observeForever {}
        viewModel.metrics.observeForever {}
        return viewModel
    }

    @Test
    fun `emergencyFund defaults to zero targets when there are no asset slices and no settings`() {
        val viewModel = viewModel()

        val result = viewModel.emergencyFund.value!!

        assertEquals(0L, result.monthlyExpenses)
        assertEquals(0L, result.sixMonthTarget)
        assertEquals(100, result.sixMonthPercent)
    }

    @Test
    fun `emergencyFund recomputes when asset slices change`() {
        val viewModel = viewModel()
        settings.value = AppSettings(monthlyLivingExpenses = 100_000)

        accounts.value = listOf(account("acc1", "Cash"))
        balances.value = listOf(balance("acc1", 150_000))

        val result = viewModel.emergencyFund.value!!
        assertEquals(600_000L, result.sixMonthTarget)
        assertEquals(25, result.sixMonthPercent)
    }

    @Test
    fun `emergencyFund recomputes when monthly expenses setting changes`() {
        val viewModel = viewModel()
        accounts.value = listOf(account("acc1", "Cash"))
        balances.value = listOf(balance("acc1", 300_000))

        settings.value = AppSettings(monthlyLivingExpenses = 100_000)

        val result = viewModel.emergencyFund.value!!
        assertEquals(600_000L, result.sixMonthTarget)
        assertEquals(50, result.sixMonthPercent)

        settings.value = AppSettings(monthlyLivingExpenses = 200_000)

        val updated = viewModel.emergencyFund.value!!
        assertEquals(1_200_000L, updated.sixMonthTarget)
        assertEquals(25, updated.sixMonthPercent)
    }

    @Test
    fun `emergencyFund excludes asset balances without a cash-and-equivalents liquidity level`() {
        val viewModel = viewModel()
        settings.value = AppSettings(monthlyLivingExpenses = 100_000)

        accounts.value = listOf(
            account("acc1", "Cash", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
            account("acc2", "Brokerage Stocks", liquidityLevel = LiquidityLevels.STOCKS),
            account("acc3", "Unclassified Asset", liquidityLevel = null)
        )
        balances.value = listOf(
            balance("acc1", 150_000),
            balance("acc2", 1_000_000),
            balance("acc3", 500_000)
        )

        val result = viewModel.emergencyFund.value!!
        assertEquals(600_000L, result.sixMonthTarget)
        assertEquals(25, result.sixMonthPercent)
    }

    @Test
    fun `metrics combines total equity, cash and emergency fund percent`() {
        val viewModel = viewModel()
        settings.value = AppSettings(monthlyLivingExpenses = 100_000)

        accounts.value = listOf(
            account("cash", "Cash", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS),
            account("capital", "Owner Capital", type = "equity", liquidityLevel = null)
        )
        balances.value = listOf(balance("cash", 150_000), balance("capital", 900_000))

        val result = viewModel.metrics.value!!
        assertEquals(900_000L, result.totalEquity)
        assertEquals(150_000L, result.cash)
        assertEquals(25, result.emergencyFundPercent)
        assertEquals(17, result.cashToEquityPercent)
    }

    @Test
    fun `metrics recomputes when monthly expenses setting changes`() {
        val viewModel = viewModel()
        accounts.value =
            listOf(account("cash", "Cash", liquidityLevel = LiquidityLevels.CASH_AND_EQUIVALENTS))
        balances.value = listOf(balance("cash", 300_000))
        settings.value = AppSettings(monthlyLivingExpenses = 100_000)

        assertEquals(50, viewModel.metrics.value!!.emergencyFundPercent)

        settings.value = AppSettings(monthlyLivingExpenses = 200_000)

        assertEquals(25, viewModel.metrics.value!!.emergencyFundPercent)
    }

    @Test
    fun `setMonthlyLivingExpenses delegates to the settings repository`() {
        val viewModel = viewModel()

        viewModel.setMonthlyLivingExpenses(250_000)

        org.mockito.kotlin.verify(settingsRepository)
            .setMonthlyLivingExpenses(250_000)
    }
}
