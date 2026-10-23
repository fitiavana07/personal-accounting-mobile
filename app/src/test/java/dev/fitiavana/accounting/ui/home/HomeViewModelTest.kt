package dev.fitiavana.accounting.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.accounts.LiquidityLevels
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.alerts.AlertCheckManager
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.exchangerates.RateRefresher
import dev.fitiavana.accounting.features.exchangerates.RefreshResult
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.p2pprices.P2pPriceRepository
import dev.fitiavana.accounting.features.p2pprices.P2pPrices
import dev.fitiavana.accounting.network.p2p.P2pPaymentMethod
import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.settings.AppSettingsRepository
import dev.fitiavana.accounting.ui.reports.ReportPeriodSelector
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HomeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var balanceRepository: BalanceRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var instrumentRepository: InstrumentRepository
    private lateinit var exchangeRateRepository: ExchangeRateRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var p2pPriceRepository: P2pPriceRepository
    private lateinit var rateRefresher: RateRefresher
    private lateinit var alertCheckManager: AlertCheckManager

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
        p2pPriceRepository = mock()
        rateRefresher = mock()
        alertCheckManager = mock()

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
            settingsRepository,
            p2pPriceRepository,
            rateRefresher,
            alertCheckManager
        )
        // MediatorLiveData only forwards source updates while it has an active observer.
        viewModel.emergencyFund.observeForever {}
        viewModel.metrics.observeForever {}
        return viewModel
    }

    @Test
    fun `refreshRates refreshes the rates then checks the alerts against them`() {
        val result = RefreshResult(listOf("BTC"), emptyList(), null)
        whenever(rateRefresher.refresh()).thenReturn(result)
        val viewModel = viewModel()

        assertEquals(result, viewModel.refreshRates())

        val inOrder = org.mockito.Mockito.inOrder(rateRefresher, alertCheckManager)
        inOrder.verify(rateRefresher).refresh()
        inOrder.verify(alertCheckManager).check()
    }

    @Test
    fun `refreshRates still returns the result when the alert check fails`() {
        val result = RefreshResult(listOf("BTC"), emptyList(), null)
        whenever(rateRefresher.refresh()).thenReturn(result)
        whenever(alertCheckManager.check()).thenThrow(RuntimeException("boom"))
        val viewModel = viewModel()

        assertEquals(result, viewModel.refreshRates())
    }

    @Test
    fun `refreshP2pPrices publishes the repository result`() {
        val prices = P2pPrices(buy = null, sell = emptyList())
        whenever(p2pPriceRepository.fetch()).thenReturn(prices)
        val viewModel = viewModel()

        viewModel.refreshP2pPrices()

        assertEquals(prices, viewModel.p2pPrices.value)
    }

    @Test
    fun `selectPaymentMethod saves the choice then refreshes the prices`() {
        val prices = P2pPrices(buy = emptyList(), sell = emptyList(), filter = P2pPaymentMethod("Mvola", "Mvola"))
        whenever(p2pPriceRepository.fetch()).thenReturn(prices)
        val viewModel = viewModel()

        viewModel.selectPaymentMethod("Mvola")

        val inOrder = org.mockito.Mockito.inOrder(p2pPriceRepository)
        inOrder.verify(p2pPriceRepository).selectMethod("Mvola")
        inOrder.verify(p2pPriceRepository).fetch()
        assertEquals(prices, viewModel.p2pPrices.value)
    }

    @Test
    fun `payment methods and current selection come from the repository`() {
        val methods = listOf(P2pPaymentMethod("Mvola", "Mvola"))
        whenever(p2pPriceRepository.getPaymentMethods()).thenReturn(methods)
        whenever(p2pPriceRepository.getSelectedMethod()).thenReturn("Mvola")
        val viewModel = viewModel()

        assertEquals(methods, viewModel.getPaymentMethods())
        assertEquals("Mvola", viewModel.getSelectedPaymentMethod())
    }

    @Test
    fun `emergencyFund defaults to zero targets when there are no asset slices and no settings`() {
        val viewModel = viewModel()

        val result = viewModel.emergencyFund.value!!

        assertEquals(0L, result.monthlyExpenses)
        assertEquals(0L, result.sixMonthTarget)
        assertEquals(100, result.sixMonthPercent)
    }

    private fun pinnableAccounts() {
        val instrument = Instrument(code = "BTC", note = "", type = "cryptocurrency", decimalPlaces = 8, coingeckoId = "bitcoin")
        val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)
        instruments.value = listOf(instrument, usdt)
        accounts.value = listOf("a", "b").map {
            Account(id = it, name = it, type = "asset", instrumentCode = "BTC", intermediaryInstrumentCode = "USDT")
        }
        balances.value = listOf(balance("a", 50_000), balance("b", 50_000))
    }

    @Test
    fun `homeItems puts the pinned account first`() {
        val viewModel = viewModel()
        viewModel.homeItems.observeForever {}
        pinnableAccounts()

        settings.value = AppSettings(monthlyLivingExpenses = 0, pinnedAccountId = "b")

        assertEquals(listOf("b", "a"), viewModel.homeItems.value!!.map { it.accountId })
    }

    @Test
    fun `togglePin delegates to the settings repository`() {
        viewModel().togglePin("a")

        verify(settingsRepository).togglePin("a")
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
        assertEquals(100_000L, result.monthlyExpenses)
        assertEquals(1.5, result.cashRunwayMonths, 0.0001)
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

    @Test
    fun `computeMonthlyNetIncomesSync computes net income for each of the last 6 full months`() {
        val viewModel = viewModel()
        val now = System.currentTimeMillis()
        val earliest = now - 400L * 24 * 60 * 60 * 1000

        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(account("revenue", "Salary", type = "revenue", liquidityLevel = null))
        )
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(earliest to now)
        whenever(balanceRepository.computeBalancesBetween(any(), any()))
            .thenReturn(mapOf("revenue" to 100_000L))

        val result = viewModel.computeMonthlyNetIncomesSync()

        val expectedMonths = ReportPeriodSelector.lastNFullMonths(now, 6, earliest)
        assertEquals(List(expectedMonths.size) { 100_000L }, result)
        expectedMonths.forEach { ym ->
            verify(balanceRepository).computeBalancesBetween(
                ReportPeriodSelector.startOfMonthMillis(ym.year, ym.month),
                ReportPeriodSelector.endOfMonthMillis(ym.year, ym.month)
            )
        }
    }

    @Test
    fun `incomeToExpenses combines monthly net incomes and monthly expenses`() {
        val viewModel = viewModel()
        settings.value = AppSettings(monthlyLivingExpenses = 200_000)
        viewModel.incomeToExpenses.observeForever {}

        whenever(accountRepository.getAllSync()).thenReturn(emptyList())
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(null)
        viewModel.refreshMonthlyNetIncomesSync()

        assertEquals(200_000L, viewModel.incomeToExpenses.value!!.monthlyExpenses)
        assertEquals(0L, viewModel.incomeToExpenses.value!!.averageMonthlyIncome)
        assertEquals(0, viewModel.incomeToExpenses.value!!.percent)
    }

    @Test
    fun `metrics includes incomeToExpensesPercent once monthly net incomes are computed`() {
        val viewModel = viewModel()
        settings.value = AppSettings(monthlyLivingExpenses = 100_000)
        val now = System.currentTimeMillis()
        val earliest = now - 400L * 24 * 60 * 60 * 1000

        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(account("revenue", "Salary", type = "revenue", liquidityLevel = null))
        )
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(earliest to now)
        whenever(balanceRepository.computeBalancesBetween(any(), any()))
            .thenReturn(mapOf("revenue" to 125_000L))

        viewModel.refreshMonthlyNetIncomesSync()

        assertEquals(125, viewModel.metrics.value!!.incomeToExpensesPercent)
    }
}
